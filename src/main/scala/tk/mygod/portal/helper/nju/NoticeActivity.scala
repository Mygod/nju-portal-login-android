package tk.mygod.portal.helper.nju

import java.text.DateFormat
import java.util.Date

import android.content.pm.ShortcutManager
import android.graphics.Typeface
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener
import android.support.v7.widget.{DefaultItemAnimator, LinearLayoutManager, RecyclerView}
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.TextView
import be.mygod.app.{CircularRevealActivity, ToolbarActivity}
import be.mygod.os.Build
import be.mygod.util.Conversions._
import tk.mygod.portal.helper.nju.database.Notice

/**
  * @author Mygod
  */
final class NoticeActivity extends ToolbarActivity with CircularRevealActivity with OnRefreshListener
  with TypedFindView {
  private final class NoticeViewHolder(view: View) extends RecyclerView.ViewHolder(view) with View.OnClickListener {
    {
      val typedArray = obtainStyledAttributes(Array(android.R.attr.selectableItemBackground))
      view.setBackgroundResource(typedArray.getResourceId(0, 0))
      typedArray.recycle()
    }
    private var item: Notice = _
    private val text1 = itemView.findViewById(android.R.id.text1).asInstanceOf[TextView]
    private val text2 = itemView.findViewById(android.R.id.text2).asInstanceOf[TextView]
    itemView.setOnClickListener(this)

    def bind(item: Notice) {
      this.item = item
      text1.setText(item.formattedTitle)
      val date = new Date(item.distributionTime * 1000)
      //noinspection ScalaDeprecation
      val summary = getString(R.string.notice_summary, DateFormat.getDateTimeInstance(
        DateFormat.DEFAULT, DateFormat.DEFAULT, getResources.getConfiguration.locale).format(date))
      text2.setText(if (item.url == null) summary else summary + '\n' + item.url)
      val alpha = if (item.obsolete) .5F else 1F
      val style = if (item.read) Typeface.NORMAL else Typeface.BOLD
      text1.setAlpha(alpha)
      text2.setAlpha(alpha)
      text1.setTypeface(null, style)
      text2.setTypeface(null, style)
    }

    def onClick(v: View) {
      if (!item.read) {
        text1.setTypeface(null, Typeface.NORMAL)
        text2.setTypeface(null, Typeface.NORMAL)
      }
      NoticeManager.read(item)
      if (item.url != null) launchUrl(item.url)
    }
  }

  private final class NoticeAdapter extends RecyclerView.Adapter[NoticeViewHolder] {
    var notices = new Array[Notice](0)
    def getItemCount: Int = notices.length
    def onBindViewHolder(vh: NoticeViewHolder, i: Int): Unit = vh.bind(notices(i))
    def onCreateViewHolder(vg: ViewGroup, i: Int) = new NoticeViewHolder(LayoutInflater.from(vg.getContext)
      .inflate(android.R.layout.simple_list_item_2, vg, false))
  }

  private var swiper: SwipeRefreshLayout = _
  private val adapter = new NoticeAdapter

  override protected def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_notices)
    configureToolbar()
    setNavigationIcon()
    swiper = findView(TR.swiper)
    swiper.setColorSchemeResources(R.color.material_accent_200, R.color.material_primary_500)
    swiper.setOnRefreshListener(this)
    adapter.notices = NoticeManager.fetchAllNotices.toArray
    val notices = findView(TR.notices)
    notices.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false))
    notices.setItemAnimator(new DefaultItemAnimator)
    notices.setAdapter(adapter)
    if (Build.version >= 25) getSystemService(classOf[ShortcutManager]).reportShortcutUsed("notice")
  }

  override def onResume() {
    super.onResume()
    onRefresh
  }

  def onRefresh(): Unit = if (!swiper.isRefreshing) {
    swiper.setRefreshing(true)
    ThrowableFuture {
      NoticeManager.updateUnreadNotices()
      adapter.notices = NoticeManager.fetchAllNotices.toArray
      runOnUiThread(() => {
        adapter.notifyDataSetChanged()
        swiper.setRefreshing(false)
      })
    }
  }
}
