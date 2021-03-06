/*
 * Copyright 2015 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package no.schedule.javazone.v3.session;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.AppBarLayout.OnOffsetChangedListener;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.ShareCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.support.v4.widget.NestedScrollView;
import android.support.v4.widget.NestedScrollView.OnScrollChangeListener;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.transition.Transition;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.appindexing.Thing;
import com.google.android.gms.common.api.GoogleApiClient;

import java.util.ArrayList;
import java.util.List;

import no.schedule.javazone.v3.BuildConfig;
import no.schedule.javazone.v3.Config;
import no.schedule.javazone.v3.R;
import no.schedule.javazone.v3.archframework.UpdatableView;
import no.schedule.javazone.v3.feedback.SessionFeedbackActivity;
import no.schedule.javazone.v3.injection.ModelProvider;
import no.schedule.javazone.v3.schedule.ScheduleDayAdapter;
import no.schedule.javazone.v3.session.SessionDetailModel.SessionDetailUserActionEnum;
import no.schedule.javazone.v3.ui.UIUtils;
import no.schedule.javazone.v3.ui.widget.CheckableFloatingActionButton;
import no.schedule.javazone.v3.util.AnalyticsHelper;
import no.schedule.javazone.v3.util.ImageLoader;
import no.schedule.javazone.v3.util.LogUtils;
import no.schedule.javazone.v3.util.SessionsHelper;
import no.schedule.javazone.v3.util.TimeUtils;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static no.schedule.javazone.v3.provider.ScheduleContract.*;
import static no.schedule.javazone.v3.schedule.SessionItemViewHolder.*;
import static no.schedule.javazone.v3.session.SessionDetailModel.*;
import static no.schedule.javazone.v3.util.LogUtils.LOGD;

/**
 * Displays the details about a session. The user can add/remove a session from the schedule, watch
 * a live stream if available, watch the session on YouTube, view the map, share the session, and
 * submit feedback.
 */
public class SessionDetailFragment extends Fragment implements
        UpdatableView<SessionDetailModel, SessionDetailModel.SessionDetailQueryEnum, SessionDetailUserActionEnum>,
        Callbacks {

    private static final String TAG = LogUtils.makeLogTag(SessionDetailFragment.class);
    private static final long HEADER_FADE_DURATION = 300L;

    private SessionDetailPresenter mPresenter;

    private CheckableFloatingActionButton mAddScheduleFab;

    private CoordinatorLayout mCoordinatorLayout;

    private AppBarLayout mAppBar;

    private CollapsingToolbarLayout mCollapsingToolbar;

    private ViewGroup mToolbar;

    private TextView mToolbarTitle;

    private ImageView mBackButton;

    private ImageView mShareButton;

    //private ImageView mMapButton;

    private NestedScrollView mScrollView;

    private TextView mTitle;

    private TextView mSubtitle;

    private TextView mAbstract;

    private Button mWatchVideo;

    private LinearLayout mTags;

    private ViewGroup mTagsContainer;

    private Button mFeedbackButton;

    private View mPhotoViewContainer;

    private ImageView mPhotoView;

    private TextView mRelatedSessionsLabel;

    private RecyclerView mRelatedSessions;

    private ScheduleDayAdapter mRelatedSessionsAdapter;

    private View mEmptyView;

    private ImageLoader mImageLoader;

    private Runnable mTimeHintUpdaterRunnable = null;

    private List<Runnable> mDeferredUiOperations = new ArrayList<>();

    private Handler mHandler;

    private boolean mAnalyticsScreenViewHasFired;

    private UserActionListener<SessionDetailUserActionEnum> mListener;

    private boolean mShowFab = false;

    private boolean mHasEnterTransition = false;

    private GoogleApiClient mClient;

    private float mToolbarTitleAlpha;

    private float mHeaderImageAlpha;

    private boolean mHasHeaderImage;

    private ColorStateList mIconTintNormal;

    private ColorStateList mIconTintCollapsing;

    private long mHeaderAnimDuration;

    @Override
    public void addListener(UserActionListener<SessionDetailUserActionEnum> listener) {
        mListener = listener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        mAnalyticsScreenViewHasFired = false;
        mClient = new GoogleApiClient.Builder(getActivity())
                .addApi(AppIndex.API)
                .enableAutoManage(getActivity(), null)
                .build();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.session_detail_frag, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        mCoordinatorLayout = (CoordinatorLayout) view.findViewById(R.id.root_container);
        mCoordinatorLayout.setStatusBarBackground(null);

        mAppBar = (AppBarLayout) view.findViewById(R.id.appbar);
        mCollapsingToolbar =
                (CollapsingToolbarLayout) mAppBar.findViewById(R.id.collapsing_toolbar);
        mCollapsingToolbar.setStatusBarScrim(null);
        mToolbar = (ViewGroup) mCollapsingToolbar.findViewById(R.id.session_detail_toolbar);
        mToolbarTitle = (TextView) mToolbar.findViewById(R.id.toolbar_title);
        mToolbarTitleAlpha = mToolbarTitle.getAlpha();
        mPhotoViewContainer = mCollapsingToolbar.findViewById(R.id.session_photo_container);
        mPhotoView = (ImageView) mPhotoViewContainer.findViewById(R.id.session_photo);

        mScrollView = (NestedScrollView) view.findViewById(R.id.scroll_view);
        mScrollView.setOnScrollChangeListener(new OnScrollChangeListener() {
            @Override
            public void onScrollChange(NestedScrollView v, int scrollX, int scrollY, int oldScrollX,
                                       int oldScrollY) {
                if (scrollY > mTitle.getBottom()) {
                    fadeInToolbarTitle();
                    setToolbarTint(mIconTintCollapsing);

                } else {
                    fadeOutToolbarTitle();
                    setToolbarTint(mIconTintNormal);
                }
            }
        });
        final ViewGroup details = (ViewGroup) view.findViewById(R.id.details_container);
        mTitle = (TextView) details.findViewById(R.id.session_title);
        mSubtitle = (TextView) details.findViewById(R.id.session_subtitle);
        mAbstract = (TextView) details.findViewById(R.id.session_abstract);
        mTags = (LinearLayout) details.findViewById(R.id.session_tags);
        mTagsContainer = (ViewGroup) details.findViewById(R.id.session_tags_container);
        mFeedbackButton = (Button) details.findViewById(R.id.give_feedback_button);
        mRelatedSessions = (RecyclerView) details.findViewById(R.id.related_sessions_list);
        mRelatedSessionsAdapter = new ScheduleDayAdapter(getContext(), this, false);
        mRelatedSessions.setAdapter(mRelatedSessionsAdapter);

        mEmptyView = details.findViewById(android.R.id.empty);

        mAddScheduleFab =
                (CheckableFloatingActionButton) view.findViewById(R.id.add_schedule_button);
        mAddScheduleFab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean isInSchedule = !((CheckableFloatingActionButton) view).isChecked();
                showInSchedule(isInSchedule, true);
                if (isInSchedule) {
                    sendUserAction(SessionDetailUserActionEnum.STAR, null);
                } else {
                    sendUserAction(SessionDetailUserActionEnum.UNSTAR, null);
                }

                SessionsHelper.showBookmarkClickedHint(mAddScheduleFab, isInSchedule);
                mAddScheduleFab.announceForAccessibility(isInSchedule
                        ? getString(R.string.session_details_a11y_session_added)
                        : getString(R.string.session_details_a11y_session_removed));
            }
        });

        // Set up the fake toolbar
        Context toolbarContext = mToolbar.getContext();
        mIconTintCollapsing = AppCompatResources.getColorStateList(toolbarContext,
                R.color.session_detail_toolbar_icon_tint_collapsing);
        mIconTintNormal = AppCompatResources.getColorStateList(toolbarContext,
                R.color.session_detail_toolbar_icon_tint_normal);

        mBackButton = (ImageView) mToolbar.findViewById(R.id.back);
        mBackButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().onNavigateUp();
            }
        });
        //mMapButton = (ImageView) mToolbar.findViewById(R.id.map);
        //mMapButton.setOnClickListener(new OnClickListener() {
        //    @Override
        //    public void onClick(View v) {
        //        sendUserAction(SessionDetailUserActionEnum.SHOW_MAP, null);
        //    }
        //});
        mShareButton = (ImageView) mToolbar.findViewById(R.id.share);
        mShareButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendUserAction(SessionDetailUserActionEnum.SHOW_SHARE, null);
            }
        });
        mAppBar.addOnOffsetChangedListener(new OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                if (Math.abs(verticalOffset) == appBarLayout.getTotalScrollRange()) {
                    fadeOutHeaderImage();
                } else {
                    fadeInHeaderImage();
                }
            }
        });

        mImageLoader = new ImageLoader(getContext());
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mHandler = new Handler();

        // init presenter
        SessionDetailModel model = ModelProvider.provideSessionDetailModel(
                ((SessionDetailActivity) getActivity()).getSessionUri(), getContext(),
                new SessionsHelper(getActivity()), getLoaderManager());
        mPresenter =
                new SessionDetailPresenter(model, this, SessionDetailUserActionEnum.values(),
                        SessionDetailQueryEnum.values());
        mPresenter.loadInitialQueries();
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);

        final Transition sharedElementEnterTransition =
                getActivity().getWindow().getSharedElementEnterTransition();
        if (sharedElementEnterTransition != null) {
            mHasEnterTransition = true;
            sharedElementEnterTransition.addListener(new UIUtils.TransitionListenerAdapter() {
                @Override
                public void onTransitionStart(final Transition transition) {
                    enterTransitionStarted();
                }

                @Override
                public void onTransitionEnd(final Transition transition) {
                    enterTransitionFinished();
                }
            });
        }
        final Transition sharedElementReturnTransition =
                getActivity().getWindow().getSharedElementReturnTransition();
        if (sharedElementReturnTransition != null) {
            sharedElementReturnTransition.addListener(new UIUtils.TransitionListenerAdapter() {
                @Override
                public void onTransitionStart(final Transition transition) {
                    returnTransitionStarted();
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mTimeHintUpdaterRunnable != null) {
            mHandler.postDelayed(mTimeHintUpdaterRunnable,
                    SessionDetailConstants.TIME_HINT_UPDATE_INTERVAL);
        }
        mPresenter.initListeners();
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeCallbacksAndMessages(null);
        mPresenter.cleanUpListeners();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.session_detail, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        //if (itemId == R.id.menu_map_room) {
        //    sendUserAction(SessionDetailUserActionEnum.SHOW_MAP, null);
        //    return true;
        //} else
        if (itemId == R.id.menu_share) {
            sendUserAction(SessionDetailUserActionEnum.SHOW_SHARE, null);
            return true;
        }
        return false;
    }

    private void sendUserAction(SessionDetailUserActionEnum action, Bundle args) {
        mListener.onUserAction(action, args);
    }

    @Override
    public void displayData(SessionDetailModel data, SessionDetailQueryEnum query) {
        switch (query) {
            case SESSIONS:
                displaySessionData(data);
                break;
//            case FEEDBACK:
//                updateFeedbackButton(data);
//                break;
            case SPEAKERS:
                displaySpeakersData(data);
                break;
            default:
                break;
        }
    }

    private void showInSchedule(boolean isInSchedule, boolean animate) {
        mAddScheduleFab.setChecked(isInSchedule);
        mAddScheduleFab.setContentDescription(getString(isInSchedule
                ? R.string.remove_from_schedule
                : R.string.add_to_schedule));
        if (!animate) return;

        AnimatedVectorDrawable avd = (AnimatedVectorDrawable) ContextCompat.getDrawable(
                getContext(), isInSchedule ? R.drawable.avd_bookmark : R.drawable.avd_unbookmark);
        mAddScheduleFab.setImageDrawable(avd);
        ObjectAnimator backgroundColor = ObjectAnimator.ofArgb(
                mAddScheduleFab,
                UIUtils.BACKGROUND_TINT,
                isInSchedule ? Color.WHITE
                        : ContextCompat.getColor(getContext(), R.color.jz_orange));
        backgroundColor.setDuration(400L);
        backgroundColor.setInterpolator(AnimationUtils.loadInterpolator(getContext(),
                android.R.interpolator.fast_out_slow_in));
        backgroundColor.start();
        avd.start();
    }

    @Override
    public void displayErrorMessage(SessionDetailQueryEnum query) {
        // Not showing any error
    }

    @Override
    public void displayUserActionResult(SessionDetailModel data,
                                        SessionDetailUserActionEnum userAction, boolean success) {
        switch (userAction) {
            case SHOW_MAP:
//                Intent mapIntent = new Intent(getActivity(), MapActivity.class);
//                mapIntent.putExtra(MapActivity.EXTRA_ROOM, data.getRoomId());
//                getActivity().startActivity(mapIntent);
                break;
            case SHOW_SHARE:
                ShareCompat.IntentBuilder builder = ShareCompat.IntentBuilder.from(getActivity())
                        .setType("text/plain")
                        .setText(getString(R.string.share_template, data.getSessionTitle(),
                                BuildConfig.CONFERENCE_HASHTAG, data.getSessionUrl()));
                Intent shareIntent = Intent.createChooser(
                        builder.getIntent(),
                        getString(R.string.title_share));
                getActivity().startActivity(shareIntent);
                break;
            default:
                // Other user actions are completely handled in model
                break;
        }
    }

    @Override
    public Uri getDataUri(SessionDetailQueryEnum query) {
        switch (query) {
            case SESSIONS:
                return ((SessionDetailActivity) getActivity()).getSessionUri();
            default:
                return null;
        }
    }

    @Override
    public Context getContext() {
        return getActivity();
    }

    private void displaySessionData(final SessionDetailModel data) {
        mToolbarTitle.setText(data.getSessionTitle());
        mTitle.setText(data.getSessionTitle());
        mSubtitle.setText(data.getSessionSubtitle());
        if (!TextUtils.isEmpty(data.getSessionAbstract())) {
            UIUtils.setTextMaybeHtml(mAbstract, data.getSessionAbstract());
            mAbstract.setVisibility(VISIBLE);
        } else {
            mAbstract.setVisibility(GONE);
        }

        // Handle Keynote as a special case, where the user cannot remove it
        // from the schedule (it is auto added to schedule on sync)

        showInSchedule(!data.isKeynote() && data.isInSchedule(), false);
        updateTimeBasedUi(data);
        updateEmptyView(data);
        setToolbarTint(mIconTintNormal);

        fireAnalyticsScreenView(data.getSessionTitle());

        mTimeHintUpdaterRunnable = new Runnable() {
            @Override
            public void run() {
                if (getActivity() == null) {
                    // Do not post a delayed message if the activity is detached.
                    return;
                }
                updateTimeBasedUi(data);
                mHandler.postDelayed(mTimeHintUpdaterRunnable,
                        SessionDetailConstants.TIME_HINT_UPDATE_INTERVAL);
            }
        };
        mHandler.postDelayed(mTimeHintUpdaterRunnable,
                SessionDetailConstants.TIME_HINT_UPDATE_INTERVAL);

        if (!mHasEnterTransition) {
            // No enter transition so update UI manually
            enterTransitionFinished();
        }

//        mPhotoView.setColorFilter(getContext().getResources().getColor(
//                R.color.theme_primary));
        mPhotoView.setBackgroundColor(getContext().getResources().getColor(
                R.color.theme_primary));
        mPhotoView.setImageResource(R.drawable.ic_logo);
    }

    private void setToolbarTint(ColorStateList tintList) {
        mBackButton.setImageTintList(tintList);
        //mMapButton.setImageTintList(tintList);
        mShareButton.setImageTintList(tintList);
    }

    private void enterTransitionStarted() {
        mAddScheduleFab.setVisibility(INVISIBLE);
        mToolbar.setAlpha(0f);
    }

    /**
     * Finish any UI setup that should be deferred until the enter transition has completed.
     */
    private void enterTransitionFinished() {
        if (mShowFab) {
            mAddScheduleFab.show();
        }
        if (mToolbar.getAlpha() != 1f) {
            mToolbar.animate()
                    .alpha(1f).setDuration(200L)
                    .setInterpolator(new LinearOutSlowInInterpolator())
                    .start();
        }
    }

    private void returnTransitionStarted() {
        // Fade the header bar for a smoother transition.
        final ObjectAnimator color = ObjectAnimator.ofInt(mAppBar, UIUtils.BACKGROUND_COLOR,
                ContextCompat.getColor(getContext(), R.color.background));
        color.setEvaluator(new ArgbEvaluator());
        color.setDuration(200L);
        color.start();
        // Also fade out the toolbar and FAB
        mToolbar.animate()
                .alpha(0f)
                .setDuration(200L)
                .start();
        mAddScheduleFab.hide();
    }

    /**
     * Sends a screen view to Google Analytics, if a screenview hasn't already been sent since the
     * fragment was loaded.  This prevents background syncs from causing superflous screen views.
     *
     * @param sessionTitle The name of the session being tracked.
     */
    private void fireAnalyticsScreenView(String sessionTitle) {
        if (!mAnalyticsScreenViewHasFired) {
            // ANALYTICS SCREEN: View the Session Details page for a specific session.
            // Contains: The session title and session ID.
            AnalyticsHelper.sendScreenView("Session: " + sessionTitle, this.getActivity());
            mAnalyticsScreenViewHasFired = true;
        }
    }

    private void displaySpeakersData(SessionDetailModel data) {
        final ViewGroup speakersGroup = (ViewGroup) getActivity()
                .findViewById(R.id.session_speakers_block);
        speakersGroup.removeAllViews();

        final LayoutInflater inflater = getActivity().getLayoutInflater();
        List<SessionDetailModel.Speaker> speakers = data.getSpeakers();
        for (Speaker speaker : speakers) {
            View speakerView = inflater.inflate(R.layout.speaker_detail, speakersGroup, false);


            ImageView speakerImage = (ImageView) speakerView.findViewById(R.id.speaker_image);
            TextView speakerName = (TextView) speakerView.findViewById(R.id.speaker_name);
            TextView speakerCompany = (TextView) speakerView.findViewById(R.id.speaker_company);
            TextView speakerTwitter = (TextView) speakerView.findViewById(R.id.speaker_twitter);
            TextView speakerAbstract = (TextView) speakerView.findViewById(R.id.speaker_abstract);

            speakerName.setText(speaker.getName());
            if (TextUtils.isEmpty(speaker.getCompany())) {
                speakerCompany.setVisibility(GONE);
            } else {
                speakerCompany.setText(speaker.getCompany());
            }

            if(TextUtils.isEmpty(speaker.getTwitterUrl())) {
                speakerTwitter.setVisibility(GONE);
            } else {
                String twitterUrl = "<a href=\"https://twitter.com/" + speaker.getTwitterUrl().substring(1) + "\">"
                    + speaker.getTwitterUrl() +"</a>";
                speakerTwitter.setMovementMethod(LinkMovementMethod.getInstance());
                speakerTwitter.setText(Html.fromHtml(twitterUrl));
            }


            if (!TextUtils.isEmpty(speaker.getImageUrl()) && mImageLoader != null) {
                mImageLoader.loadImage(speaker.getImageUrl(), speakerImage);
            }
            UIUtils.setTextMaybeHtml(speakerAbstract, speaker.getAbstract());

            speakersGroup.addView(speakerView);
        }

        speakersGroup.setVisibility(speakersGroup.getChildCount() > 0 ? VISIBLE : GONE);
        updateEmptyView(data);
    }

    private void displayRelatedSessions(SessionDetailModel data) {
        mRelatedSessionsAdapter.updateItems(data.getRelatedSessions());
        int visibility = mRelatedSessionsAdapter.getItemCount() > 0 ? VISIBLE : GONE;
        mRelatedSessions.setVisibility(visibility);
        mRelatedSessionsLabel.setVisibility(visibility);
    }

    private void updateEmptyView(SessionDetailModel data) {
        mEmptyView.setVisibility(data.hasSummaryContent() ? GONE : VISIBLE);
    }

    private void updateTimeBasedUi(SessionDetailModel data) {
        // If the session is done, hide the FAB, and show the feedback button.
        mShowFab = !data.isKeynote();
        if (mShowFab) {
            mAddScheduleFab.show();
        } else {
            mAddScheduleFab.hide();
        }
        updateFeedbackButton(data);

        String timeHint = "";

        if (TimeUtils.hasConferenceEnded(getContext())) {
            // No time hint to display.
            timeHint = "";
        } else if (data.hasSessionEnded()) {
            timeHint = getString(R.string.time_hint_session_ended);
        } else if (data.isSessionOngoing()) {
            long minutesAgo = data.minutesSinceSessionStarted();
            if (minutesAgo > 1) {
                timeHint = getString(R.string.time_hint_started_min, minutesAgo);
            } else {
                timeHint = getString(R.string.time_hint_started_just);
            }
        } else {
            long minutesUntilStart = data.minutesUntilSessionStarts();
            if (minutesUntilStart > 0
                    && minutesUntilStart <= SessionDetailConstants.HINT_TIME_BEFORE_SESSION_MIN) {
                if (minutesUntilStart > 1) {
                    timeHint = getString(R.string.time_hint_about_to_start_min, minutesUntilStart);
                } else {
                    timeHint = getString(R.string.time_hint_about_to_start_shortly);
                }
            }
        }

        final TextView timeHintView = (TextView) getActivity().findViewById(R.id.time_hint);

        if (!TextUtils.isEmpty(timeHint)) {
            timeHintView.setVisibility(VISIBLE);
            timeHintView.setText(timeHint);
        } else {
            timeHintView.setVisibility(GONE);
        }
    }

    private void updateFeedbackButton(final SessionDetailModel data) {
        if (!data.hasFeedback() && data.hasSessionEnded()) {
            mFeedbackButton.setVisibility(VISIBLE);
            mFeedbackButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendUserAction(SessionDetailUserActionEnum.GIVE_FEEDBACK, null);
                   Intent intent = data.getFeedbackIntent();
                   startActivity(intent);
                }
            });
        } else {
            mFeedbackButton.setVisibility(GONE);
            mFeedbackButton.setOnClickListener(null);
        }
    }

    private void fadeInToolbarTitle() {
        if (mToolbarTitleAlpha < 1f) {
            mToolbarTitleAlpha = 1f;
            mToolbarTitle.animate().alpha(mToolbarTitleAlpha).start();
        }
    }

    private void fadeOutToolbarTitle() {
        if (mToolbarTitleAlpha > 0f) {
            mToolbarTitleAlpha = 0f;
            mToolbarTitle.animate().alpha(mToolbarTitleAlpha).start();
        }
    }

    private void fadeInHeaderImage() {
        if (mHeaderImageAlpha < 1f) {
            mHeaderImageAlpha = 1f;
            mPhotoViewContainer.animate()
                    .setDuration(HEADER_FADE_DURATION)
                    .alpha(mHeaderImageAlpha)
                    .start();
        }
    }

    private void fadeOutHeaderImage() {
        if (mHeaderImageAlpha > 0f) {
            mHeaderImageAlpha = 0f;
            mPhotoViewContainer.animate()
                    .setDuration(HEADER_FADE_DURATION)
                    .alpha(mHeaderImageAlpha)
                    .start();
        }
    }

    // -- Adapter callbacks (for related sessions)

    @Override
    public void onSessionClicked(String sessionId) {
        startActivity(new Intent(Intent.ACTION_VIEW, Sessions.buildSessionUri(sessionId)));
    }

    @Override
    public boolean bookmarkingEnabled() {
        return true;
    }

    @Override
    public void onBookmarkClicked(String sessionId, boolean isInSchedule) {
        Bundle args = new Bundle();
        args.putString(Sessions.SESSION_ID, sessionId);
        SessionDetailUserActionEnum action = isInSchedule
                ? SessionDetailUserActionEnum.UNSTAR_RELATED
                : SessionDetailUserActionEnum.STAR_RELATED;
        sendUserAction(action, args);
    }

    @Override
    public boolean feedbackEnabled() {
        return false;
    }

    @Override
    public void onFeedbackClicked(String sessionId, String sessionTitle) {
        SessionFeedbackActivity.launchFeedback(getContext(), sessionId);
    }
}
