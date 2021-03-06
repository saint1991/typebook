/*
 * Copyright (c) 2017 CyberAgent
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package jp.co.cyberagent.typebook.api

import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.http.{Method, Request, Response}
import com.twitter.finagle.stats.DefaultStatsReceiver
import com.twitter.io.Buf
import com.twitter.logging.Logger
import com.twitter.util.Future
import io.finch
import io.finch._
import io.finch.syntax._

object RegistryService {

    private val log = Logger.get(getClass)

    object AccessLogger extends SimpleFilter[Request, Response] {
        override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
            val message = s"${request.method} ${request.uri} from ${request.remoteSocketAddress}"
            request.method match {
                case Method.Post | Method.Put| Method.Delete => log.info(message)
                case _ => log.debug(message)
            }
            service(request)
        }
    }

    object AccessCount extends SimpleFilter[Request, Response] {
        final val CounterName = "access_counter"
        override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
            DefaultStatsReceiver.counter(CounterName).incr()
            service(request)
        }
    }

    // for health checking
    val health: Endpoint[String] = get("health") apply Ok("OK")


    import io.finch.circe._
    import io.circe.generic.auto._
    import jp.co.cyberagent.typebook.api.ErrorHandling.errorEncoder
    implicit val longTextEncoder: finch.Encode.Text[Long] = Encode.text[Long]((l, cs) => Buf.ByteArray.Owned(l.toString.getBytes(cs)) )

    def apply(): Service[Request, Response] = AccessLogger.andThen(AccessCount.andThen(
        Bootstrap.configure(includeServerHeader = false)
          .serve[Application.Json](SubjectService.jsonEndpoints :+: ConfigService.jsonEndpoints :+: SchemaService.jsonEndpoints)
          .serve[Text.Plain](SubjectService.textEndpoints :+: ConfigService.textEndpoints :+: health)
          .toService
    ))
}
