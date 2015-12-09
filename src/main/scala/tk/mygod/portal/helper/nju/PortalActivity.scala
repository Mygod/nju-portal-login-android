package tk.mygod.portal.helper.nju

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener
import android.support.v7.widget.Toolbar.OnMenuItemClickListener
import android.util.Base64
import android.view.MenuItem
import android.webkit.{CookieManager, WebView, WebViewClient}
import tk.mygod.app.ToolbarActivity
import tk.mygod.os.Build
import tk.mygod.util.Conversions._
import tk.mygod.util.MetricsUtils

/**
  * Use built-in WebViews to bypass Chrome Custom Tab's Data Saver feature.
  *
  * @author Mygod
  */
object PortalActivity {
  private final val COOKIE_URL = PortalManager.ROOT_URL + "/portal"
}

class PortalActivity extends ToolbarActivity with TypedFindView with OnRefreshListener with OnMenuItemClickListener {
  import PortalActivity._

  private lazy val webView = findView(TR.webView)
  private lazy val webViewSettings = webView.getSettings
  private var mobileUA: String = _
  private lazy val desktopUA = mobileUA.replaceAll("; Android .+?(?=[;)])", "").replaceAll(" Mobile", "")
  private lazy val desktopSiteMenu = toolbar.getMenu.findItem(R.id.action_desktop_site)

  override protected def onCreate(savedInstanceState: Bundle) {
    val manager = CookieManager.getInstance
    manager.setAcceptCookie(true)
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_webview)
    configureToolbar(0)
    toolbar.setTitle(PortalManager.DOMAIN)
    toolbar.inflateMenu(R.menu.activity_webview)
    toolbar.setOnMenuItemClickListener(this)
    val swiper = findView(TR.swiper)
    swiper.setOnRefreshListener(this)
    swiper.setColorSchemeResources(R.color.material_accent_500, R.color.material_primary_500)
    webView.setBackgroundColor(0xfff4f4f4)
    mobileUA = webViewSettings.getUserAgentString
    webViewSettings.setJavaScriptEnabled(true)
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
    webView.post(setDesktopSite(webView.getWidth >= MetricsUtils.dp2px(this, 875)))
  }

  def setDesktopSite(enabled: Boolean) {
    if (desktopSiteMenu.isChecked != enabled) {
      desktopSiteMenu.setChecked(enabled)
      webViewSettings.setUserAgentString(if (enabled) desktopUA else mobileUA)
    }
    onRefresh
  }

  def onMenuItemClick(menuItem: MenuItem) = menuItem.getItemId match {
    case R.id.action_desktop_site =>
      setDesktopSite(!menuItem.isChecked)
      true
    case R.id.action_browser =>
      launchUrl(Uri.parse(PortalManager.ROOT_URL))
      finish
      true
    case _ => false
  }
  def onRefresh = webView.loadUrl(PortalManager.ROOT_URL)
}
