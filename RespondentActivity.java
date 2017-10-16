package cn.tianya.light.ui;


import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import cn.tianya.bo.ClientRecvObject;
import cn.tianya.bo.Entity;
import cn.tianya.bo.EntityCacheject;
import cn.tianya.bo.ForumNote;
import cn.tianya.bo.RecommendQA;
import cn.tianya.cache.CacheDataManager;
import cn.tianya.light.R;
import cn.tianya.light.adapter.PagerAdapterEx;
import cn.tianya.light.adapter.QALoopScrollAdapter;
import cn.tianya.light.bo.QuestionType;
import cn.tianya.light.config.ConfigurationEx;
import cn.tianya.light.config.impl.AndroidConfiguration;
import cn.tianya.light.fragment.BaseFragment;
import cn.tianya.light.fragment.RespondentFragment;
import cn.tianya.light.module.ActivityBuilder;
import cn.tianya.light.module.UpbarSimpleListener.OnUpbarButtonClickListener;
import cn.tianya.light.network.QuestionConnector;
import cn.tianya.light.pulltorefresh.PullToRefreshListView;
import cn.tianya.light.pulltorefresh.extras.ScrollableHelper;
import cn.tianya.light.pulltorefresh.extras.ScrollableLayout;
import cn.tianya.light.util.StyleUtils;
import cn.tianya.light.view.LabelLayout;
import cn.tianya.light.view.TabChannelIndicator;
import cn.tianya.light.view.UpbarView;
import cn.tianya.light.vision.Exception.NetworkErrorException;
import cn.tianya.light.widget.AutoScrollViewPagerHelper;
import cn.tianya.light.widget.EmptyViewHelper;
import cn.tianya.log.Log;
import cn.tianya.network.ForumConnector;
import cn.tianya.util.ContextUtils;
import cn.tianya.util.DateUtils;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;


/**
 * Created by wuchunmei on 9/3/17.
 * 天涯答人
 */
public class RespondentActivity extends FragmentActivityBase implements OnUpbarButtonClickListener, QALoopScrollAdapter.OnViewClickListener, ScrollableHelper.ScrollableContainer {
    private TabChannelIndicator mTabIndicator;
    protected List<Entity> mTitles;
    private UpbarView mUpbarView;
    private LabelLayout mChannelLayout;
    private RespondentFragment mFragment;
    protected Disposable mDisposable;
    private ViewPager mViewPager;
    private ViewPagerAdapter mViewPagerAdapter;
    private View mEmptyView;
    private EmptyViewHelper mEmptyViewHelper;
    private Button mRefreshButton;
    private static final String CACHE_KEY_QUESTION_TYPE = "question_type";
    private RespondentFragment mRespondentFragment;

    private boolean mIsShowCarouselFigure = false; //是否显示轮播图
    private RelativeLayout mCarouselFigureLayout;
    private AutoScrollViewPagerHelper mLoopPrevHelper;
    private static final int QA_LOOP_CACHE_OUTDATED = 10;     // 顶部运营轮播图缓存失效时间， 10min
    private List<Entity> mPicList = new ArrayList<>();
    private static final String KEY_RESPONDENT_CAROUSELFIGURE = "key_respondent_carouselfigure";
    private ScrollableLayout scrollableLayout;
    private ConfigurationEx mConfiguration;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.respondent_activity);
        mTitles = new ArrayList<>();
        init();
    }

    private void init() {
        mConfiguration = new AndroidConfiguration(this);
        initialView();
        getQuestionTypeList(CACHE_KEY_QUESTION_TYPE);
        onNightModeChanged();
    }

    private void initialView() {
        mUpbarView = (UpbarView) findViewById(R.id.top);
        mUpbarView.setWindowTitle("");
        mUpbarView.setCenterButtonText(getTitleResId());
        mUpbarView.setUpbarCallbackListener(this);
        mViewPager = (ViewPager) findViewById(R.id.viewPager);
        mChannelLayout = (LabelLayout) findViewById(R.id.label_layout);
        mChannelLayout.setTitleResId(getTitleResId());
        mEmptyView = findViewById(R.id.tab_empty);
        mEmptyViewHelper = new EmptyViewHelper(this, mEmptyView);
        mEmptyViewHelper.setViewEnabled(false);
        mRefreshButton = (Button) findViewById(R.id.refresh_btn);
        mRefreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getQuestionTypeList(CACHE_KEY_QUESTION_TYPE);
            }
        });


        scrollableLayout = (ScrollableLayout) findViewById(R.id.scrollableLayout);
        scrollableLayout.getHelper().setCurrentScrollableContainer(this);
    }


    /**
     * 初始化轮播图配置
     *
     * @param list
     */
    private void initCarouselFigureLayout(List<Entity> list) {
        mLoopPrevHelper = new AutoScrollViewPagerHelper(this);
        mLoopPrevHelper.initQaPrevViewPager(list, this);
        mCarouselFigureLayout.removeAllViews();
        mCarouselFigureLayout.addView(mLoopPrevHelper.getLayout());
    }


    private void startLoop() {
        Log.d("wu", "living===>>> startLoop====");
        if (mLoopPrevHelper != null) {
            mLoopPrevHelper.startAutoSwitch();
        }
    }

    public void pauseLoop() {
        Log.d("wu", "living===>>> pauseLoop====");
        if (mLoopPrevHelper != null) {
            mLoopPrevHelper.stopAutoSwitch();
        }
    }

    /**
     * 设置是否显示轮播图
     *
     * @param isShowCarouselFigure
     */
    public void setIsShowCarouselFigure(boolean isShowCarouselFigure) {
        if (isShowCarouselFigure) {
            mCarouselFigureLayout = (RelativeLayout) findViewById(R.id.respondent_head_layout);
            mCarouselFigureLayout.setVisibility(View.VISIBLE);
            getCarouselFigures(KEY_RESPONDENT_CAROUSELFIGURE);
            startLoop();
        }
    }

    protected int getTitleResId() {
        return R.string.tianya_respondent;
    }

    private void fillTabView(List<Entity> list) {
        if (list != null) {
            mChannelLayout.setTitles(list);
            mViewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager(), getBaseFragmentList());
            mViewPager.setAdapter(mViewPagerAdapter);
            mTabIndicator = mChannelLayout.getTabChannel();
            mTabIndicator.setItemWidth((int) getResources().getDimension(R.dimen.tab_item_width));
            //这里会回调mTabIndicator的notifyDataSetChanged(pagerAdapter);
            mTabIndicator.setViewPager(mViewPager);
        }
    }

    protected void showEmptyView(boolean isShow) {
        if (isShow) {
            mChannelLayout.setVisibility(View.GONE);
            mViewPager.setVisibility(View.GONE);
            mEmptyView.setVisibility(View.VISIBLE);
            mEmptyViewHelper.setNoNetworkEmptyView(true);
            mEmptyViewHelper.setErrorEmptyView();
            mEmptyViewHelper.setTipText(R.string.note_empty_network);
        } else {
            mChannelLayout.setVisibility(View.VISIBLE);
            mViewPager.setVisibility(View.VISIBLE);
            mEmptyView.setVisibility(View.GONE);
        }
    }




    /**
     * 获取轮播运营图数据
     *
     * @param cacheKey
     */
    private void getCarouselFigures(final String cacheKey) {
        mDisposable = Observable.create(new ObservableOnSubscribe<List<Entity>>() {
            @Override
            public void subscribe(ObservableEmitter<List<Entity>> e) throws Exception {
                ClientRecvObject clientRecvObject = null;
                List<Entity> list = null;
                EntityCacheject cacheject = CacheDataManager.getDataFromCache(RespondentActivity.this, cacheKey);
                //判断缓存是否存在
                if (cacheject != null && cacheject.getCacheData() != null) {
                    boolean isExpired = false;
                    isExpired = DateUtils.checkExpire(cacheject.getLastUpdateDate(), QA_LOOP_CACHE_OUTDATED);
                    //如果缓存过期，则重新请求
                    if (isExpired) {
                        if (ContextUtils.checkNetworkConnection(RespondentActivity.this)) {
                            clientRecvObject = QuestionConnector.getCarouselFigure(RespondentActivity.this);
                            if (clientRecvObject != null && clientRecvObject.isSuccess()) {
                                list = (ArrayList<Entity>) clientRecvObject.getClientData();
                                CacheDataManager.setDataToCache(RespondentActivity.this, cacheKey, (Serializable) list);
                            }
                        }
                    } else {
                        //如果缓存存在且不过期，则读缓存
                        list = (List<Entity>) cacheject.getCacheData();
                    }
                    if (list != null) {
                        e.onNext(list);
                        e.onComplete();
                    }
                } else {
                    //缓存不存在，则请求服务器接口
                    if (ContextUtils.checkNetworkConnection(RespondentActivity.this)) {
                        clientRecvObject = ForumConnector.getRecommendQAPic(RespondentActivity.this);
                        if (clientRecvObject != null && clientRecvObject.isSuccess()) {
                            list = (ArrayList<Entity>) clientRecvObject.getClientData();
                            CacheDataManager.setDataToCache(RespondentActivity.this, cacheKey, (Serializable) list);
                        }
                        if (list != null) {
                            e.onNext(list);
                            e.onComplete();
                        }
                    }

                }

            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Consumer<List<Entity>>() {
            @Override
            public void accept(List<Entity> list) throws Exception {
                if (list.size() != 0) {
                    mPicList = list;
                    initCarouselFigureLayout(list);
                }
            }
        });

    }

    /**
     * 获取问题类型列表
     *
     * @param cacheKey
     */
    protected void getQuestionTypeList(final String cacheKey) {
        mDisposable = Observable.create(new ObservableOnSubscribe<List<Entity>>() {
            @Override
            public void subscribe(ObservableEmitter<List<Entity>> e) throws Exception {
                ClientRecvObject clientRecvObject = null;
                List<Entity> list = null;
                EntityCacheject listEntityCacheject = CacheDataManager.getDataFromCache(RespondentActivity.this, cacheKey);
                //判断缓存是否存在
                if (listEntityCacheject != null && listEntityCacheject.getCacheData() != null) {
                    list = (ArrayList<Entity>) listEntityCacheject.getCacheData();
                    if (list != null && list.size() != 0) {
                        e.onNext(list);
                        e.onComplete();
                    } else {
                        e.onNext(new ArrayList<Entity>(1));
                        e.onComplete();
                    }
                } else {
                    //缓存不存在，则请求服务器接口
                    if (ContextUtils.checkNetworkConnection(RespondentActivity.this)) {
                        clientRecvObject = QuestionConnector.getResponderTypeList(RespondentActivity.this, 0);
                        if (clientRecvObject != null && clientRecvObject.isSuccess()) {
                            list = (ArrayList<Entity>) clientRecvObject.getClientData();
                            CacheDataManager.setDataToCache(RespondentActivity.this, cacheKey, (Serializable) list);
                        }
                        if (list != null && list.size() != 0) {
                            e.onNext(list);
                            e.onComplete();
                        } else {
                            e.onNext(new ArrayList<Entity>(1));
                        }
                    } else {
                        e.onError(new NetworkErrorException());
                    }

                }
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribeWith(new DisposableObserver<List<Entity>>() {
            @Override
            public void onNext(List<Entity> list) {
                fillData(list);
            }

            @Override
            public void onError(Throwable e) {
                if (e instanceof NetworkErrorException) {
                    showEmptyView(true);
                }
            }

            @Override
            public void onComplete() {

            }
        });

    }

    protected void resetTabList() {
    }

    protected void fillData(List<Entity> list) {
        if (list != null && list.size() != 0) {
            mTitles.clear();
            mTitles.addAll(list);
            resetTabList();
            fillTabView(mTitles);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ActivityBuilder.ACTIVITY_RESULT_LOGIN) {
            if (mRespondentFragment != null) {
                mRespondentFragment.onActivityResult(requestCode, resultCode, data);
            }
            return;
        }
    }

    @Override
    public void onUpbarButtonClick(View view, int index, String action) {
        if (index == OnUpbarButtonClickListener.TOPBUTTONINDEX_LEFTBUTTON) {
            this.finish();
        }
    }

    @Override
    public void onNightModeChanged() {
        mUpbarView.onNightModeChanged();
        mChannelLayout.onNightModeChanged();
        mEmptyViewHelper.onNightModeChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDisposable != null) {
            mDisposable.dispose();
        }
        pauseLoop();
    }

    @Override
    public void onPrevViewClick(int position) {
        RecommendQA recommendQA = null;
        String url = null;
        String categoryId = null;
        int noteId = 0;
        if (mPicList != null && mPicList.size() != 0) {
            recommendQA = (RecommendQA) mPicList.get(position);
            if (recommendQA != null) {
                url = recommendQA.getUrl().trim();
                categoryId = recommendQA.getCategoryId();
                noteId = recommendQA.getNoteId();

            }
        }
        if (categoryId != null && noteId != 0) {
            ForumNote forumNote = new ForumNote();
            forumNote.setCategoryId(categoryId);
            forumNote.setNoteId(noteId);
            ActivityBuilder.openNoteActivity(this, mConfiguration, forumNote);
        } else {
            ActivityBuilder.showWebActivity(this, url, WebViewActivity.WebViewEnum.WEB);
        }
    }



    @Override
    public View getScrollableView() {
        PullToRefreshListView listView = ((BaseFragment) mViewPagerAdapter.getItem(mViewPager.getCurrentItem())).getRefreshListView();
        if (listView != null) {
            return listView.getRefreshableView();
        } else {
            return null;
        }
    }

    public class ViewPagerAdapter extends PagerAdapterEx {

        private ArrayList<BaseFragment> fragmentsList;

        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        public ViewPagerAdapter(FragmentManager fm, ArrayList<BaseFragment> fragmentsList) {
            super(fm);
            this.fragmentsList = fragmentsList;
        }

        @Override
        public int getCount() {
            return mTitles == null ? 0 : mTitles.size();
        }

        @Override
        public Fragment getItem(int position) {

            return fragmentsList.get(position);


        }

        @Override
        public View getTabView(int position) {
            View itemView = getLayoutInflater().inflate(R.layout.tab_indicator_item, null);
            TextView tab = (TextView) itemView.findViewById(R.id.menu_text);
            tab.setTextColor(StyleUtils.getColorOnMode(getApplicationContext(), R.color.color_444444));
            QuestionType type = (QuestionType) mTitles.get(position);
            if (type != null) {
                String name = type.getName();
                if (name != null) {
                    tab.setText(name);
                }
            }
            return itemView;
        }
    }

    protected ArrayList<BaseFragment> getBaseFragmentList() {
        ArrayList<BaseFragment> fragments = new ArrayList<>();
        if (mTitles != null && mTitles.size() != 0) {
            for (Entity entity : mTitles) {
                QuestionType type = (QuestionType) entity;
                BaseFragment fragment = getBaseFragment(type);
                fragments.add(fragment);
            }
        }
        return fragments;
    }

    protected BaseFragment getBaseFragment(QuestionType type) {
        mRespondentFragment = RespondentFragment.getInstance(type);
        return mRespondentFragment;
    }

}
