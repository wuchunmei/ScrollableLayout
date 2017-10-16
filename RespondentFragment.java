package cn.tianya.light.fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


import cn.tianya.bo.ClientRecvObject;
import cn.tianya.bo.Entity;
import cn.tianya.bo.User;
import cn.tianya.light.adapter.RespondentListAdapter;
import cn.tianya.light.bo.QuestionType;
import cn.tianya.light.R;
import cn.tianya.light.bo.RespondentSubscribeBo;
import cn.tianya.light.config.ConfigurationEx;

import cn.tianya.light.config.impl.AndroidConfiguration;
import cn.tianya.light.module.ActivityBuilder;
import cn.tianya.light.network.QuestionConnector;
import cn.tianya.light.profile.UserProfileActivity;
import cn.tianya.light.pulltorefresh.PullToRefreshBase;
import cn.tianya.light.pulltorefresh.PullToRefreshListView;
import cn.tianya.light.ui.BeMasterActivity;
import cn.tianya.light.ui.IssueQuestionActivity;
import cn.tianya.light.ui.LoginActivity;
import cn.tianya.light.ui.RespondentActivity;
import cn.tianya.light.util.Constants;
import cn.tianya.light.view.EntityListView;
import cn.tianya.light.vision.Exception.NetworkErrorException;
import cn.tianya.light.widget.EmptyViewHelper;
import cn.tianya.user.LoginUserManager;
import cn.tianya.util.ContextUtils;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by wuchunmei on 9/3/17.
 * 天涯答人
 */
public class RespondentFragment extends BaseFragment implements View.OnClickListener, AdapterView.OnItemClickListener {
    private PullToRefreshListView mPullToRefreshListView;
    private View mRootView;
    private EmptyViewHelper mEmptyViewHelper;
    private RespondentListAdapter mAdapter;
    private Button mRefreshButton;
    private Button mNoResponerBtn;
    private ConfigurationEx mConfiguration;
    private Disposable mDisposable;
    private int tagId;
    private int mCurPageIndex = 1;
    private static final int TYPE_LOAD_DATA = 1;//初始化拉数据
    private static final int TYPE_LOAD_MORE = 2; //上拉获取更多数据
    private boolean isShowFooter = false;


    private final ArrayList<Entity> mResultList = new ArrayList<Entity>();

    public RespondentFragment() {
        super();
    }

    public static RespondentFragment getInstance(QuestionType type) {
        RespondentFragment fragment = new RespondentFragment();
        if (type != null) {
            Bundle bundle = new Bundle();
            bundle.putString("mTypeId", type.getId());
            fragment.setArguments(bundle);
        }
        return fragment;
    }

    @Override
    public void onResume() {
        super.onResume();
        mResultList.clear();
        loadChannel(1, TYPE_LOAD_DATA);
        RespondentActivity activity = (RespondentActivity) getActivity();
        activity.setIsShowCarouselFigure(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        tagId = Integer.parseInt(getArguments().getString("mTypeId"));
        mConfiguration = new AndroidConfiguration(this.getActivity());
        initView(inflater, container);
        onNightModeChanged();
        return mRootView;
    }

    private void initView(LayoutInflater inflater, ViewGroup container) {
        mRootView = inflater.inflate(R.layout.fragment_respondent, container, false);
        View emptyView = mRootView.findViewById(R.id.empty);
        mEmptyViewHelper = new EmptyViewHelper(getActivity(), emptyView);
        mEmptyViewHelper.setViewEnabled(false);
        mRefreshButton = (Button) mRootView.findViewById(R.id.refresh_btn);
        mRefreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mResultList.clear();
                loadChannel(1, TYPE_LOAD_DATA);
            }
        });
        mNoResponerBtn = (Button) mRootView.findViewById(R.id.btn_tip);
        mNoResponerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setClass(RespondentFragment.this.getActivity(), BeMasterActivity.class);
                startActivity(intent);
            }
        });
        mAdapter = new RespondentListAdapter(mResultList, this.getActivity(), this);
        mPullToRefreshListView = (PullToRefreshListView) mRootView.findViewById(R.id.listview);
        mPullToRefreshListView.setAdapter(mAdapter);
        mPullToRefreshListView.setOnItemClickListener(this);
        mPullToRefreshListView.setMode(PullToRefreshBase.Mode.PULL_FROM_END);
        mPullToRefreshListView.setEmptyView(emptyView);
        mPullToRefreshListView.setOnRefreshListener(new PullToRefreshBase.OnRefreshListener2<ListView>() {
            @Override
            public void onPullDownToRefresh(PullToRefreshBase<ListView> refreshView) {
            }

            @Override
            public void onPullUpToRefresh(PullToRefreshBase<ListView> refreshView) {
                loadChannel(mCurPageIndex + 1, TYPE_LOAD_MORE);
            }
        });
    }

    private void loadChannel(final int pageIndex, final int type) {
        mDisposable = Observable.create(new ObservableOnSubscribe<List<Entity>>() {
            @Override
            public void subscribe(ObservableEmitter<List<Entity>> e) throws Exception {
                final int PAGESIZE = 20;
                if (ContextUtils.checkNetworkConnection(RespondentFragment.this.getActivity())) {
                    ClientRecvObject clientRecvObject = QuestionConnector.getRespondentList(RespondentFragment.this.getActivity(), 0, tagId, pageIndex, PAGESIZE, 0, "");
                    if (clientRecvObject != null && clientRecvObject.isSuccess()) {
                        List<Entity> list = (ArrayList<Entity>) clientRecvObject.getClientData();
                        if (list != null && list.size() > 0) {
                            e.onNext(list);
                            e.onComplete();
                        } else {
                            e.onNext(new ArrayList<Entity>(1));
                            e.onComplete();
                        }
                    }
                } else {
                    e.onError(new NetworkErrorException());
                }

            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribeWith(new DisposableObserver<List<Entity>>() {
            @Override
            public void onNext(List<Entity> list) {
                if (list != null && list.size() > 0) {
                    //这里只有上拉加载的需求，所以数据直接追加在list后面
                    mCurPageIndex = pageIndex;// 成功取到数据后更新当前页数
                    isShowFooter = false;
                    mResultList.addAll(removeSelf(list));
                    mEmptyViewHelper.setNoNetworkEmptyView(false);
                    mAdapter.notifyDataSetChanged();
                } else {
                    if (type == TYPE_LOAD_MORE && !isShowFooter) {
                        View info = View.inflate(RespondentFragment.this.getActivity(), R.layout.respender_footer_info, null);
                        ((TextView) info.findViewById(R.id.textViewInfo))
                                .setText(R.string.note_footer_no_more_data);
                        mPullToRefreshListView.getRefreshableView().addFooterView(info);
                        isShowFooter = true;
                    } else if (type == TYPE_LOAD_DATA) {
                        //// TODO: 9/21/17  请求接口,没有答主的情况下，产品让去开通答主(找专家页耦合了开通答主的逻辑)
                        //如果没有答主就去开通答主
                        mEmptyViewHelper.setNoNetworkEmptyView(false);
                        mEmptyViewHelper.setErrorEmptyView();
                        //// TODO: 9/22/17 被这脑残的产品整废了，别问我这里为啥设置０,因为半脑的产品昨天刚叫加上，今天因为测试提了一下，又让干掉 
                        //不要开通答主按钮
                        mEmptyViewHelper.setTipBtnText(0);
                        mEmptyViewHelper.setTipText(R.string.no_responder);
                    }
                }
            }

            @Override
            public void onError(Throwable e) {
                if (e instanceof NetworkErrorException) {
                    ContextUtils.showToast(getActivity(), R.string.noconnectionremind);
                    mEmptyViewHelper.setNoNetworkEmptyView(true);
                }
            }

            @Override
            public void onComplete() {
                if (mPullToRefreshListView.isRefreshing()) {
                    mPullToRefreshListView.onRefreshComplete();
                }

            }
        });

    }

    /**
     * 把自己给过滤掉,自己不能给自己做答主
     *
     * @param list
     * @return
     */
    private List<Entity> removeSelf(List<Entity> list) {
        Iterator<Entity> sListIterator = list.iterator();
        User user = LoginUserManager.getLoginedUser(mConfiguration);
        while (sListIterator.hasNext()) {
            RespondentSubscribeBo entity = (RespondentSubscribeBo) sListIterator.next();
            if (user != null && entity != null && entity.getExpertId() == user.getLoginId()) {
                sListIterator.remove();
            }
        }
        return list;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDisposable.dispose();
    }

    @Override
    public void onClick(View v) {
        String expertName = null;
        int money = 0;
        int expertId = 0;
        RespondentSubscribeBo responder = (RespondentSubscribeBo) v.getTag();
        if (responder != null) {
            expertName = responder.getExpertName();
            money = (int) responder.getPrice();
            expertId = responder.getExpertId();
            if (!LoginUserManager.isLogined(mConfiguration)) {
                ActivityBuilder.showLoginActivityForResult(this, LoginActivity.SHOW_LOGIN_TYPE_LOGIN,
                        ActivityBuilder.ACTIVITY_RESULT_LOGIN);
            } else {
                Intent intent = new Intent(this.getActivity(), IssueQuestionActivity.class);
                intent.putExtra(Constants.RESPONDENT_NAME, expertName);
                intent.putExtra(Constants.RESPONDENT_MONEY, money);
                intent.putExtra(Constants.RESPONDENT_ID, expertId);
                intent.putExtra(Constants.IS_FROM_RESPONDENT, true);
                this.getActivity().startActivity(intent);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            return;
        }
        if (requestCode == ActivityBuilder.ACTIVITY_RESULT_LOGIN) {
            loadChannel(1, TYPE_LOAD_DATA);
        }
    }

    @Override
    public void onNightModeChanged() {
        if (mEmptyViewHelper != null) {
            mEmptyViewHelper.onNightModeChanged();
        }
        if (mPullToRefreshListView != null) {
            EntityListView.initList(mPullToRefreshListView.getRefreshableView());
            mPullToRefreshListView.setNightModeChanged();
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
            mPullToRefreshListView.getRefreshableView().setDivider(null);
        }
    }


    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        RespondentSubscribeBo responder = (RespondentSubscribeBo) parent.getItemAtPosition(position);
        User user = new User();
        user.setLoginId(responder.getExpertId());
        user.setUserName(responder.getExpertName());
        ActivityBuilder.showProfileActivity(RespondentFragment.this.getActivity(), user, UserProfileActivity.QUESTION_POSITION);
    }

    /**
     * 返回一个ListView,activity做顶部停靠需要
     * @return PullToRefreshListView
     */
    @Override
    public PullToRefreshListView getRefreshListView() {
        return mPullToRefreshListView;
    }
}
