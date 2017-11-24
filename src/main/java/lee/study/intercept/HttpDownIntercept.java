package lee.study.intercept;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.*;
import lee.study.HttpDownServer;
import lee.study.down.HttpDown;
import lee.study.model.HttpDownInfo;
import lee.study.model.TaskInfo;
import lee.study.proxyee.intercept.HttpProxyIntercept;

public class HttpDownIntercept extends HttpProxyIntercept {

  private HttpRequest httpRequest;
  private boolean downFlag = false;

  @Override
  public boolean beforeRequest(Channel channel, HttpRequest httpRequest) {
    this.httpRequest = httpRequest;
    return true;
  }

  @Override
  public boolean afterResponse(Channel clientChannel, Channel proxyChannel,
      final HttpResponse httpResponse) {
    downFlag = false;
    if ((httpResponse.status().code() + "").indexOf("20") == 0) { //响应码为20x
      HttpHeaders httpHeaders = httpResponse.headers();
      String disposition = httpHeaders.get(HttpHeaderNames.CONTENT_DISPOSITION);
      if (disposition != null) {  //先根据CONTENT_DISPOSITION:ATTACHMENT来判断是否下载请求
        //没有Range请求头(audio标签发起的)并且不是ajax请求(没有X-Requested-With请求头)
        if (disposition.contains(HttpHeaderValues.ATTACHMENT) && !httpRequest.headers()
            .contains(HttpHeaderNames.RANGE) && !httpRequest.headers()
            .contains("x-requested-with")) {
          downFlag = true;
        }
      }
      if (!downFlag) {  //再根据URL和CONTENT_TYPE来判断是否下载请求
        if (httpRequest.uri().matches("^.*\\.[^./]{1,5}$")) { //url后缀为.xxx
          String contentType = httpHeaders.get(HttpHeaderNames.CONTENT_TYPE);
          if (contentType != null
              && contentType.contains("application/")
              && !contentType.contains("javascript")
              && !contentType.contains("x-navimap")
              && !contentType.contains("font-")
              && !contentType.contains("json")
              && !contentType.contains("shockwave-flash")) {
            //字体文件情况排除 referer为.css后缀 uri为.woff或.ttf
            try {
              String referer = httpRequest.headers().get(HttpHeaderNames.REFERER);
              if (referer != null && contentType.contains("application/octet-stream") &&
                  (referer.matches("^.*\\.(?i)css[^.]*$") || httpRequest.uri()
                      .matches("^.*\\.(?i)(?:wof|tt)f[^.]*$"))) {
                downFlag = false;
              } else {
                downFlag = true;
              }
            } catch (Exception e) {
              e.printStackTrace();
            }

          }
        }
      }
      if (downFlag) {   //如果是下载，跳转到前端下载页面
        System.out.println("=====================下载===========================");
        System.out.println(httpRequest.toString());
        System.out.println("------------------------------------------------");
        System.out.println(httpResponse.toString());
        System.out.println("================================================");
        final HttpHeaders resHeaders = new DefaultHttpHeaders();
        for (String key : httpResponse.headers().names()) {
          resHeaders.set(key, httpHeaders.get(key));
        }
        TaskInfo taskInfo = HttpDown
            .getTaskInfo(httpRequest, resHeaders, HttpDownServer.loopGroup);
        HttpDownInfo httpDownInfo = new HttpDownInfo(taskInfo, httpRequest, resHeaders);
        HttpDownServer.downContent.put(httpDownInfo.getId(),httpDownInfo);
        httpHeaders.clear();
        httpResponse.setStatus(HttpResponseStatus.OK);
        httpHeaders.set(HttpHeaderNames.CONTENT_TYPE, "text/html");
        String js = "<script>window.top.location.href='https://localhost:8443/#/newTask/" + (
            HttpDownServer.downContent.size() - 1) + "';</script>";
        HttpContent content = new DefaultLastHttpContent();
        content.content().writeBytes(js.getBytes());
        httpHeaders.set(HttpHeaderNames.CONTENT_LENGTH, js.getBytes().length);
        clientChannel.writeAndFlush(httpResponse);
        clientChannel.writeAndFlush(content);
        clientChannel.close();
        clientChannel.close();
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean afterResponse(Channel channel, Channel proxyChannel, HttpContent httpContent) {
    if (downFlag) { //如果是下载丢弃真实服务器数据
      return false;
    }
    return true;
  }
}
