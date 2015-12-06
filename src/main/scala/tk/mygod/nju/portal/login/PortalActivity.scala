package tk.mygod.nju.portal.login

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener
import android.util.Base64
import android.webkit.{CookieManager, WebView, WebViewClient}
import tk.mygod.app.ToolbarActivity
import tk.mygod.os.Build

/**
  * Use built-in WebViews to bypass Chrome Custom Tab's Data Saver feature.
  *
  * @author Mygod
  */
object PortalActivity {
  final val COOKIE_URL = "http://p.nju.edu.cn/portal"
}

class PortalActivity extends ToolbarActivity with TypedFindView with OnRefreshListener {
  import PortalActivity._

  private lazy val webView = findView(TR.webView)

  override protected def onCreate(savedInstanceState: Bundle) {
    val manager = CookieManager.getInstance
    manager.setAcceptCookie(true)
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_webview)
    configureToolbar(0)
    toolbar.setTitle(PortalManager.DOMAIN)
    val swiper = findView(TR.swiper)
    swiper.setOnRefreshListener(this)
    swiper.setColorSchemeResources(R.color.material_accent_500, R.color.material_primary_500)
    webView.setBackgroundColor(0xfff4f4f4)
    webView.getSettings.setJavaScriptEnabled(true)
    webView.setWebViewClient(new WebViewClient {
      override def onPageFinished(view: WebView, url: String) = swiper.setRefreshing(false)
      override def onPageStarted(view: WebView, url: String, favicon: Bitmap) = swiper.setRefreshing(true)
      override def shouldOverrideUrlLoading(view: WebView, url: String) = {
        val uri = Uri.parse(url)
        if ("http".equalsIgnoreCase(uri.getScheme) && PortalManager.DOMAIN.equalsIgnoreCase(uri.getHost)) false else {
          launchUrl(uri)
          true
        }
      }
    })
    //noinspection ScalaDeprecation
    if (Build.version >= 21) manager.removeSessionCookies(null) else manager.removeSessionCookie
    manager.setCookie(COOKIE_URL, "username=" + Base64.encodeToString(PortalManager.username.getBytes, Base64.DEFAULT))
    manager.setCookie(COOKIE_URL, "password=" + Base64.encodeToString(PortalManager.password.getBytes, Base64.DEFAULT))
    manager.setCookie(COOKIE_URL, "rmbUser=true")
    onRefresh
  }

  override def onBackPressed = if (webView.canGoBack) webView.goBack else super.onBackPressed
  def onRefresh = webView.loadUrl("http://p.nju.edu.cn")
}
