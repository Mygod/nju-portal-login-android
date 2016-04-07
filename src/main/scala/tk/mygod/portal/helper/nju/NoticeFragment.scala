package tk.mygod.portal.helper.nju

import java.text.DateFormat
import java.util.Date

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener
import android.support.v7.widget.{DefaultItemAnimator, LinearLayoutManager, RecyclerView}
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.TextView
import tk.mygod.app.{ActivityPlus, CircularRevealFragment, ToolbarTypedFindView}
import tk.mygod.portal.helper.nju.TypedResource._
import tk.mygod.portal.helper.nju.database.Notice
import tk.mygod.util.Conversions._

/**
  * @author Mygod
  */
final class NoticeFragment extends CircularRevealFragment with OnRefreshListener {
  private final class NoticeViewHolder(view: View) extends RecyclerView.ViewHolder(view) with View.OnClickListener {
    {
      val typedArray = getActivity.obtainStyledAttributes(Array(android.R.attr.selectableItemBackground))
      view.setBackgroundResource(typedArray.getResourceId(0, 0))
      typedArray.recycle
    }
    private var item: Notice = _
    private val text1 = itemView.findViewById(android.R.id.text1).asInstanceOf[TextView]
    private val text2 = itemView.findViewById(android.R.id.text2).asInstanceOf[TextView]
    itemView.setOnClickListener(this)

    def bind(item: Notice) {
      this.item = item
      text1.setText(item.formattedTitle)
      val date = new Date(item.distributionTime * 1000)
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
      if (item.url != null) getActivity.asInstanceOf[ActivityPlus].launchUrl(item.url)
    }
  }

  private final class NoticeAdapter extends RecyclerView.Adapter[NoticeViewHolder] {
    var notices = new Array[Notice](0)
    def getItemCount = notices.length
    def onBindViewHolder(vh: NoticeViewHolder, i: Int) = vh.bind(notices(i))
    def onCreateViewHolder(vg: ViewGroup, i: Int) = new NoticeViewHolder(LayoutInflater.from(vg.getContext)
      .inflate(android.R.layout.simple_list_item_2, vg, false))
  }

  private var swiper: SwipeRefreshLayout = _
  private val adapter = new NoticeAdapter

  override def layout = R.layout.fragment_notices
  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    super.onViewCreated(view, savedInstanceState)
    configureToolbar(R.string.notices)
    setNavigationIcon(ToolbarTypedFindView.BACK)
    swiper = view.findView(TR.swiper)
    swiper.setColorSchemeResources(R.color.material_accent_500, R.color.material_primary_500)
    swiper.setOnRefreshListener(this)
    adapter.notices = NoticeManager.fetchAllNotices.toArray
    val notices = view.findView(TR.notices)
    notices.setLayoutManager(new LinearLayoutManager(getActivity))
    notices.setItemAnimator(new DefaultItemAnimator)
    notices.setAdapter(adapter)
  }

  override def onAttach(activity: Activity) {
    //noinspection ScalaDeprecation
    super.onAttach(activity)
    activity.asInstanceOf[MainActivity].noticeFragment = this
  }

  override def onResume {
    super.onResume
    onRefresh
    NoticeManager.cancelAllNotices
  }

  def onRefresh {
    swiper.setRefreshing(true)
    ThrowableFuture {
      NoticeManager.updateUnreadNotices()
      adapter.notices = NoticeManager.fetchAllNotices.toArray
      runOnUiThread {
        adapter.notifyDataSetChanged
        swiper.setRefreshing(false)
      }
    }
  }
}
