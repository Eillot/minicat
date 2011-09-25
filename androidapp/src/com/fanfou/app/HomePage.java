package com.fanfou.app;

import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;

import com.fanfou.app.adapter.BaseCursorAdapter;
import com.fanfou.app.adapter.MessageCursorAdapter;
import com.fanfou.app.adapter.StatusCursorAdapter;
import com.fanfou.app.adapter.ViewsAdapter;
import com.fanfou.app.api.DirectMessage;
import com.fanfou.app.api.Status;
import com.fanfou.app.config.Commons;
import com.fanfou.app.db.Contents.BasicColumns;
import com.fanfou.app.db.Contents.DirectMessageInfo;
import com.fanfou.app.db.Contents.StatusInfo;
import com.fanfou.app.service.NotificationService;
import com.fanfou.app.ui.ActionBar;
import com.fanfou.app.ui.ActionBar.Action;
import com.fanfou.app.ui.UIManager;
import com.fanfou.app.ui.viewpager.CirclePageIndicator;
import com.fanfou.app.ui.widget.EndlessListView;
import com.fanfou.app.ui.widget.EndlessListView.OnRefreshListener;
import com.fanfou.app.util.OptionHelper;
import com.fanfou.app.util.Utils;

/**
 * @author mcxiaoke
 * @version 1.0 2011.05.31
 * @version 2.0 2011.07.16
 * @version 3.0 2011.09.24
 * 
 */
public class HomePage extends BaseActivity implements OnPageChangeListener,
		OnRefreshListener, OnItemLongClickListener, OnClickListener {

	public static final int PAGE_NUMS = 4;

	private ActionBar mActionBar;

	private Handler mHandler;
	private ViewPager mViewPager;
	private ViewsAdapter mViewAdapter;
	private CirclePageIndicator mPageIndicator;
	private ImageView iWriteBottom;

	private int mCurrentPage;

	private EndlessListView[] views = new EndlessListView[PAGE_NUMS];
	private Cursor[] cursors = new Cursor[PAGE_NUMS];
	private BaseCursorAdapter[] adapters = new BaseCursorAdapter[PAGE_NUMS];
	private static final String[] titles = new String[] { "我的主页", "提到我的",
			"我的私信", "随便看看" };
	
	private GestureDetector mGestureDetector;

	public static final String TAG = "HomePage";

	private void log(String message) {
		Log.i(TAG, message);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (App.DEBUG)
			log("onCreate()");

		init();
		setContentView(R.layout.home);
		setActionBar();
		setWriteBottom();
		setListViews();
		setViewPager();
		setCursors();
		setAdapters();
	}
	
	private void init(){
		mHandler = new Handler();
		mGestureDetector=new GestureDetector(mContext, new GestureListener(this));
	}

	/**
	 * 初始化和设置ActionBar
	 */
	private void setActionBar() {
		mActionBar = (ActionBar) findViewById(R.id.actionbar);
		mActionBar.setTitle(titles[0]);
		mActionBar.setTitleClickListener(this);
		Intent intent = new Intent(mContext, WritePage.class);
		Action action = new ActionBar.IntentAction(mContext, intent,
				R.drawable.i_write);
		mActionBar.setRightAction(action);

	}

	private void setViewPager() {
		if (App.DEBUG) {
			log("setViewFlow page=" + mCurrentPage);
		}
		mViewAdapter = new ViewsAdapter(views);
		mViewPager = (ViewPager) findViewById(R.id.viewpager);
		mViewPager.setOnPageChangeListener(this);
		mViewPager.setAdapter(mViewAdapter);

		mPageIndicator = (CirclePageIndicator) findViewById(R.id.viewindicator);
		mPageIndicator.setViewPager(mViewPager);

		int page=getIntent().getIntExtra(Commons.EXTRA_PAGE, 0);
		mViewPager.setCurrentItem(page);
		mPageIndicator.setCurrentItem(page);
	}

	private void setWriteBottom() {
		iWriteBottom = (ImageView) findViewById(R.id.write_bottom);
		iWriteBottom.setOnClickListener(this);

		RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		final Float f = new Float(8 * App.me.density);
		final int m = f.intValue();
		lp.setMargins(m, m, m, m);
		String position = OptionHelper.readString(this,
				R.string.option_write_icon, "right");
		if (position.equals("left")) {
			lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
			iWriteBottom.setLayoutParams(lp);
		} else if (position.equals("right")) {
			lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
			lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
			iWriteBottom.setLayoutParams(lp);
		} else {
			iWriteBottom.setVisibility(View.GONE);
		}

	}

	/**
	 * 初始化并添加四个页面的ListView
	 */
	private void setListViews() {
		for (int i = 0; i < views.length; i++) {
			views[i] = new EndlessListView(this);
			views[i].setOnRefreshListener(this);
			if (i != 2) {
				views[i].setOnItemLongClickListener(this);
			}
		}
//		views[3].setOnTouchListener(this);
	}

	private Cursor initCursor(int type) {
		String where = BasicColumns.TYPE + "=?";
		String[] whereArgs = new String[] { String.valueOf(type) };
		Uri uri = StatusInfo.CONTENT_URI;
		String[] columns = StatusInfo.COLUMNS;
		if (type == DirectMessage.TYPE_NONE) {
			uri = DirectMessageInfo.CONTENT_URI;
			columns = DirectMessageInfo.COLUMNS;
		}
		return managedQuery(uri, columns, where, whereArgs, null);
	}

	private void setCursors() {
		cursors[0] = initCursor(Status.TYPE_HOME);
		cursors[1] = initCursor(Status.TYPE_MENTION);
		cursors[2] = initCursor(DirectMessage.TYPE_NONE);
		cursors[3] = initCursor(Status.TYPE_PUBLIC);
	}

	private void setAdapters() {
		adapters[0] = new StatusCursorAdapter(true, this, cursors[0]);
		adapters[2] = new MessageCursorAdapter(this, cursors[2]);
		adapters[1] = new StatusCursorAdapter(this, cursors[1]);
		adapters[3] = new StatusCursorAdapter(this, cursors[3]);

		for (int i = 0; i < adapters.length; i++) {
			views[i].setAdapter(adapters[i]);
			// views[i].setOnScrollListener(adapters[i]);
			if (cursors[i].getCount() == 0) {
				if (App.DEBUG)
					log("cursors[" + i
							+ "] is empty, remove footer and refresh.");
				views[i].removeFooter();
			}
		}
		views[3].removeFooter();

		boolean refresh = OptionHelper.readBoolean(this,
				R.string.option_refresh_on_open, false);
		if (cursors[0].getCount() == 0 || refresh) {
			views[0].setRefreshing();
		}
	}

	/**
	 * 复原列表位置
	 */
	private void restorePosition() {
		for (int i = 0; i < views.length; i++) {
			views[i].restorePosition();
		}
	}

	private void savePosition() {
		for (int i = 0; i < views.length; i++) {
			views[i].savePosition();
		}
	}

	/**
	 * 刷新，获取最新的消息
	 * 
	 * @param type
	 *            类型参数：Home/Mention/Message/Public
	 */
	private void doRetrieve(final int page, boolean doGetMore) {
		Bundle b = new Bundle();
		b.putInt(Commons.EXTRA_COUNT, 0);
		b.putInt(Commons.EXTRA_PAGE, 0);
		b.putBoolean(Commons.EXTRA_FORMAT, false);
		ResultReceiver receiver = new HomeResultReceiver(page, doGetMore);
		;
		String sinceId = null;
		String maxId = null;
		Cursor cursor = cursors[page];
		switch (page) {
		case 0:
			if (doGetMore) {
				maxId = Utils.getMaxId(cursor);
				b.putString(Commons.EXTRA_MAX_ID, maxId);
			} else {
				sinceId = Utils.getSinceId(cursor);
				b.putString(Commons.EXTRA_SINCE_ID, sinceId);
			}
			Utils.startFetchService(this, Status.TYPE_HOME, receiver, b);
			break;
		case 1:
			if (doGetMore) {
				maxId = Utils.getMaxId(cursor);
				b.putString(Commons.EXTRA_MAX_ID, maxId);
			} else {
				sinceId = Utils.getSinceId(cursor);
				b.putString(Commons.EXTRA_SINCE_ID, sinceId);
			}
			b.putString(Commons.EXTRA_SINCE_ID, sinceId);
			Utils.startFetchService(this, Status.TYPE_MENTION, receiver, b);
			break;
		case 2:
			if (cursor != null && cursor.getCount() > 0) {
				if (doGetMore) {
					cursor.moveToLast();
					DirectMessage dm2 = DirectMessage.parse(cursor);
					if (dm2 != null) {
						maxId = dm2.id;
					}
					b.putString(Commons.EXTRA_MAX_ID, maxId);
				} else {
					cursor.moveToFirst();
					DirectMessage dm1 = DirectMessage.parse(cursor);
					if (dm1 != null) {
						sinceId = dm1.id;
					}
					b.putString(Commons.EXTRA_SINCE_ID, sinceId);
				}
			}
			Utils.startFetchService(this, DirectMessage.TYPE_NONE, receiver, b);
			break;
		case 3:
			if (!doGetMore) {
				Utils.startFetchService(this, Status.TYPE_PUBLIC, receiver, b);
			}
			break;
		default:
			break;
		}
	}

	/**
	 * 载入更多，获取较旧的消息
	 */
	private void doGetMore() {
		doRetrieve(mCurrentPage, true);
	}

	/**
	 * 刷新，载入更新的消息
	 */
	private void doRefresh() {
		doRetrieve(mCurrentPage, false);
	}

	@Override
	protected void onReceived(Intent intent) {
		int type = intent.getIntExtra(Commons.EXTRA_TYPE, -1);
		int count = intent.getIntExtra(Commons.EXTRA_COUNT, 1);
		// int page=mViewFlow.getCurrentScreen();
		switch (type) {
		case NotificationService.NOTIFICATION_TYPE_HOME:
			if (cursors[0] != null) {
				cursors[0].requery();
			}
			break;
		case NotificationService.NOTIFICATION_TYPE_MENTION:
			if (cursors[1] != null) {
				cursors[1].requery();
			}
			break;
		case NotificationService.NOTIFICATION_TYPE_DM:
			if (cursors[2] != null) {
				cursors[2].requery();
			}
			break;
		default:
			break;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		restorePosition();
		if (App.DEBUG)
			log("onResume");
	}

	@Override
	protected void onPause() {
		super.onPause();
		savePosition();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	protected int getPageType() {
		return PAGE_HOME;
	}

	@Override
	protected boolean isRootScreen() {
		return true;
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		int page = getIntent().getIntExtra(Commons.EXTRA_PAGE, 0);
		if (App.DEBUG) {
			log("onNewIntent page=" + page);
		}
		mViewPager.setCurrentItem(page);
		mPageIndicator.setCurrentItem(page);

		if (page==0) {
			cursors[page].requery();
			views[page].setRefreshing();
		}
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		if (App.DEBUG)
			log("onConfigurationChanged() ");
		super.onConfigurationChanged(newConfig);
	}

	private long lastPressTime = 0;

	@SuppressWarnings("unused")
	private boolean doBackPress() {
		if (System.currentTimeMillis() - lastPressTime < 2000) {
			finish();
			// android.os.Process.killProcess(android.os.Process.myPid());
		} else {
			Utils.notify(this, "再按一次退出");
			lastPressTime = System.currentTimeMillis();
		}
		return true;
	}

	/**
	 * FetchService返回数据处理 根据resultData里面的type信息分别处理
	 */
	private class HomeResultReceiver extends ResultReceiver {
		private int i;
		private boolean doGetMore;

		public HomeResultReceiver(int page, boolean getMore) {
			super(mHandler);
			this.i = page;
			this.doGetMore = getMore;
		}

		@Override
		protected void onReceiveResult(int resultCode, Bundle resultData) {
			switch (resultCode) {
			case Commons.RESULT_CODE_FINISH:
				if (doGetMore) {
					if (i < 3) {
						// int count = resultData.getInt(Commons.EXTRA_COUNT);
						views[i].onLoadMoreComplete();
						// if (count < 20) {
						// views[i].onNoLoadMore();
						// } else {
						// views[i].onLoadMoreComplete();
						// }
					}
				} else {
					views[i].onRefreshComplete();
					if (i < 3) {
						views[i].addFooter();
					}
				}
				cursors[i].requery();
				break;
			case Commons.RESULT_CODE_ERROR:
				String errorMessage = resultData
						.getString(Commons.EXTRA_ERROR_MESSAGE);
				Utils.notify(mContext, errorMessage);
				if (doGetMore) {
					views[i].onLoadMoreComplete();
				} else {
					views[i].onRefreshComplete();
				}
				break;
			default:
				break;
			}
		}

	}

	@Override
	public void onRefresh(EndlessListView view) {
		doRefresh();
	}

	@Override
	public void onLoadMore(EndlessListView view) {
		doGetMore();
	}

	@Override
	public void onItemClick(EndlessListView view, int position) {
		final Cursor c = (Cursor) view.getItemAtPosition(position);
		if (c == null) {
			return;
		}
		if (mCurrentPage == 2) {
			Utils.goSendPage(this, c);
		} else {
			final Status s = Status.parse(c);
			if (s != null && !s.isNull()) {
				Utils.goStatusPage(this, s);
			}
		}
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view,
			int position, long id) {
		if (mCurrentPage == 2) {
			return true;
		}
		final Cursor c = (Cursor) parent.getItemAtPosition(position);
		showPopup(view, c);
		return true;
	}

	private void showPopup(final View view, final Cursor c) {
		if (c == null) {
			return;
		}
		final Status s = Status.parse(c);
		if (s == null) {
			return;
		}
		UIManager.showPopup(mContext, c, view, s);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.actionbar_title:
			goTop();
			break;
		case R.id.write_bottom:
			Intent intent = new Intent(this, WritePage.class);
			intent.putExtra(Commons.EXTRA_TYPE, WritePage.TYPE_NORMAL);
			startActivity(intent);
			break;
		default:
			break;
		}
	}

	private void goTop() {
		if (views[mCurrentPage] != null) {
			views[mCurrentPage].setSelection(0);
		}
	}

	@Override
	public void onPageScrolled(int position, float positionOffset,
			int positionOffsetPixels) {
		mPageIndicator.onPageScrolled(position, positionOffset,
				positionOffsetPixels);
	}

	@Override
	public void onPageSelected(int position) {
		mCurrentPage = position;
		mPageIndicator.onPageSelected(position);
		mActionBar.setTitle(titles[position]);
	}

	@Override
	public void onPageScrollStateChanged(int state) {
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		return mGestureDetector.onTouchEvent(ev);
	}
	
	protected void onSwipe(boolean next){
		if(App.DEBUG){
			Log.e("GestureListener", "swipe to "+(next?"next":"prev"));
		}
//		if(mCurrentPage==3&&next){
//			mViewPager.setCurrentItem(0);
//		}
	}

	private static class GestureListener extends SimpleOnGestureListener{
		private HomePage mHomePage;
		
		static final int SWIPE_MIN_DISTANCE =200;
		static final int SWIPE_THRESHOLD_VELOCITY =80;
		
		public GestureListener(HomePage homePage){
			this.mHomePage=homePage;
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {
			float x1=e1.getX();
			float y1=e1.getY();
			float x2=e2.getX();
			float y2=e2.getY();
			float hvalue=x2-x1;
			float vvalue=y2-y1;
			float hdistance=Math.abs(hvalue);
			float vdistance=Math.abs(vvalue);
			if(App.DEBUG){
				Log.e("GestureListener", "hdistance="+hvalue);
				Log.e("GestureListener", "vdistance="+vdistance);
			}
			if(hdistance>SWIPE_MIN_DISTANCE &&vdistance<SWIPE_THRESHOLD_VELOCITY ){
				mHomePage.onSwipe(hvalue<0);
				return true;
			}
			return super.onFling(e1, e2, velocityX, velocityY);
		}
		
	}

}