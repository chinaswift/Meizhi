package me.drakeet.meizhi.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.litesuits.orm.db.assit.QueryBuilder;
import com.litesuits.orm.db.model.ConflictAlgorithm;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.umeng.analytics.MobclickAgent;
import com.umeng.update.UmengUpdateAgent;
import java.util.ArrayList;
import java.util.List;
import me.drakeet.meizhi.App;
import me.drakeet.meizhi.R;
import me.drakeet.meizhi.adapter.MeizhiListAdapter;
import me.drakeet.meizhi.data.MeizhiData;
import me.drakeet.meizhi.data.休息视频Data;
import me.drakeet.meizhi.face.OnMeizhiTouchListener;
import me.drakeet.meizhi.model.Meizhi;
import me.drakeet.meizhi.ui.base.SwipeRefreshBaseActivity;
import me.drakeet.meizhi.util.AlarmManagerUtils;
import me.drakeet.meizhi.util.Once;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

public class MainActivity extends SwipeRefreshBaseActivity {

    private static final int PRELOAD_SIZE = 6;

    @Bind(R.id.rv_meizhi) RecyclerView mRecyclerView;

    MeizhiListAdapter mMeizhiListAdapter;
    List<Meizhi> mMeizhiList;
    boolean mIsFirstTimeTouchBottom = true;
    int mPage = 1;

    @Override protected int provideContentViewId() {
        return R.layout.activity_main;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ButterKnife.bind(this);
        mMeizhiList = new ArrayList<>();
        QueryBuilder query = new QueryBuilder(Meizhi.class);
        query.limit(1, 10);
        mMeizhiList.addAll(App.sDb.query(query));

        setUpRecyclerView();
        setUpUmeng();
        AlarmManagerUtils.register(this);
    }

    @Override protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        new Handler().postDelayed(() -> setRefreshing(true), 358);
        getData(true);
    }

    private void setUpUmeng() {
        MobclickAgent.updateOnlineConfig(this);
        // MobclickAgent.setDebugMode(true);
        UmengUpdateAgent.update(this);
        UmengUpdateAgent.setDeltaUpdate(false);
        UmengUpdateAgent.setUpdateOnlyWifi(false);
    }

    private void setUpRecyclerView() {
        final StaggeredGridLayoutManager layoutManager =
            new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(layoutManager);
        mMeizhiListAdapter = new MeizhiListAdapter(this, mMeizhiList);
        mRecyclerView.setAdapter(mMeizhiListAdapter);
        new Once(this).show("tip_guide_6", () -> {
            Snackbar.make(mRecyclerView, getString(R.string.tip_guide), Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.i_know, v -> {})
                .show();
        });

        mRecyclerView.addOnScrollListener(getScrollToBottomListener(layoutManager));
        mMeizhiListAdapter.setOnMeizhiTouchListener(getOnMeizhiTouchListener());
    }

    private void getData(boolean clean) {
        Subscription s = Observable.zip(sDrakeet.getMeizhiData(mPage), sDrakeet.get休息视频Data(mPage),
            (meizhi, love) -> createMeizhiDataWith休息视频Desc(meizhi, love))
            .map(meizhiData -> meizhiData.results)
            .flatMap(Observable::from)
            .toSortedList((meizhi1, meizhi2) -> meizhi2.publishedAt.compareTo(meizhi1.publishedAt))
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(meizhis -> {
                if (clean) mMeizhiList.clear();
                saveMeizhis(meizhis);
                mMeizhiList.addAll(meizhis);
                mMeizhiListAdapter.notifyDataSetChanged();
                setRefreshing(false);
            }, throwable -> loadError(throwable));
        addSubscription(s);
    }

    private void loadError(Throwable throwable) {
        throwable.printStackTrace();
        setRefreshing(false);
        Snackbar.make(mRecyclerView, R.string.snap_load_fail, Snackbar.LENGTH_LONG)
            .setAction(R.string.retry, v -> {requestDataRefresh();})
            .show();
    }

    private void saveMeizhis(List<Meizhi> meizhis) {
        App.sDb.insert(meizhis, ConflictAlgorithm.Ignore);
    }

    private MeizhiData createMeizhiDataWith休息视频Desc(MeizhiData mzData, 休息视频Data love) {
        for (int i = 0; i < mzData.results.size(); i++) {
            Meizhi m = mzData.results.get(i);
            m.desc = m.desc + " " + love.results.get(i).desc;
        }
        return mzData;
    }

    private void getData() {
        getData(/* clean */false);
    }

    RecyclerView.OnScrollListener getScrollToBottomListener(
        StaggeredGridLayoutManager layoutManager) {
        return new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView rv, int dx, int dy) {
                boolean isBottom =
                    layoutManager.findLastCompletelyVisibleItemPositions(new int[2])[1]
                        >= mMeizhiListAdapter.getItemCount() - PRELOAD_SIZE;
                if (!mSwipeRefreshLayout.isRefreshing() && isBottom) {
                    if (!mIsFirstTimeTouchBottom) {
                        mSwipeRefreshLayout.setRefreshing(true);
                        mPage += 1;
                        getData();
                    }
                    else {
                        mIsFirstTimeTouchBottom = false;
                    }
                }
            }
        };
    }

    private OnMeizhiTouchListener getOnMeizhiTouchListener() {
        return (v, meizhiView, card, meizhi) -> {
            if (meizhi == null) return;
            if (v == meizhiView) {
                Picasso.with(this).load(meizhi.url).fetch(new Callback() {
                    @Override public void onSuccess() {startPictureActivity(meizhi, meizhiView);}

                    @Override public void onError() {}
                });
            }
            else if (v == card) {
                Intent intent = new Intent(this, GankActivity.class);
                intent.putExtra(GankActivity.EXTRA_GANK_DATE, meizhi.publishedAt);
                startActivity(intent);
            }
        };
    }

    private void startPictureActivity(Meizhi meizhi, View transitView) {
        Intent i = new Intent(MainActivity.this, PictureActivity.class);
        i.putExtra(PictureActivity.EXTRA_IMAGE_URL, meizhi.url);
        i.putExtra(PictureActivity.EXTRA_IMAGE_TITLE, meizhi.desc);

        ActivityOptionsCompat optionsCompat =
            ActivityOptionsCompat.makeSceneTransitionAnimation(MainActivity.this, transitView,
                PictureActivity.TRANSIT_PIC);
        ActivityCompat.startActivity(MainActivity.this, i, optionsCompat.toBundle());
    }

    @Override public void onToolbarClick() {mRecyclerView.smoothScrollToPosition(0);}

    @OnClick(R.id.main_fab) public void onFab(View v) {
        setRefreshing(true);
        mRecyclerView.smoothScrollToPosition(0);
        requestDataRefresh();
    }

    @Override public void requestDataRefresh() {
        super.requestDataRefresh();
        //mMeizhiList.clear();
        //mPage = 1;
        //getData(/* add from db */ false);
        setRefreshing(false);
    }

    private void openGitHubTrending() {
        String url = getString(R.string.url_github_trending);
        String title = getString(R.string.action_github_trending);
        Intent intent = new Intent(this, WebActivity.class);
        intent.putExtra(WebActivity.EXTRA_URL, url);
        intent.putExtra(WebActivity.EXTRA_TITLE, title);
        startActivity(intent);
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_trending:
                openGitHubTrending();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override public void onResume() {
        super.onResume();
        MobclickAgent.onResume(this);
    }

    @Override public void onPause() {
        super.onPause();
        MobclickAgent.onPause(this);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        ButterKnife.unbind(this);
    }
}
