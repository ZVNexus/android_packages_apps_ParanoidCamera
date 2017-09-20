/*
 * Copyright (c) 2016-2017 The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.camera;

import android.animation.Animator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.AnimationDrawable;
import android.hardware.Camera.Face;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.support.annotation.LayoutRes;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import com.android.camera.imageprocessor.filter.BeautificationFilter;
import com.android.camera.ui.*;
import com.android.camera.util.CameraUtil;

import co.aospa.camera.R;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CaptureUI extends CameraUI implements FocusOverlayManager.FocusUI,
        PreviewGestures.SingleTapListener,
        CameraManager.CameraFaceDetectionCallback,
        SettingsManager.Listener,
        PauseButton.OnPauseButtonListener {

    private static final int HIGHLIGHT_COLOR = 0xff33b5e5;
    private static final String TAG = "SnapCam_CaptureUI";
    private static final int FILTER_MENU_NONE = 0;
    private static final int FILTER_MENU_IN_ANIMATION = 1;
    private static final int FILTER_MENU_ON = 2;
    private static final int ANIMATION_DURATION = 300;
    private static final int CLICK_THRESHOLD = 200;
    private static final int AUTOMATIC_MODE = 0;

    private View mPreviewCover;
    private CaptureModule mModule;
    private AutoFitSurfaceView mSurfaceViewMono;
    private SurfaceHolder mSurfaceHolderMono;
    private int mOrientation;
    private int mFilterMenuStatus;
    private PreviewGestures mGestures;
    private SettingsManager mSettingsManager;
    private TrackingFocusRenderer mTrackingFocusRenderer;
    private ImageView mThumbnail;
    private Camera2FaceView mFaceView;
    private SelfieFlashView mSelfieView;
    private float mScreenBrightness = 0.0f;

    private SurfaceHolder.Callback callbackMono = new SurfaceHolder.Callback() {
        // SurfaceHolder callbacks
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            mSurfaceHolderMono = holder;
            if (mMonoDummyOutputAllocation != null) {
                mMonoDummyOutputAllocation.setSurface(mSurfaceHolderMono.getSurface());
            }
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
        }
    };

    private SurfaceHolder.Callback callback = new SurfaceHolder.Callback() {

        // SurfaceHolder callbacks
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            CaptureUI.this.surfaceChanged(holder, format, width, height);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            CaptureUI.this.surfaceCreated(holder);
            previewUIReady();
            if (mTrackingFocusRenderer != null && mTrackingFocusRenderer.isVisible()) {
                mTrackingFocusRenderer.setSurfaceDim(getSurfaceView().getLeft(),
                        getSurfaceView().getTop(), getSurfaceView().getRight(),
                        getSurfaceView().getBottom());
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            CaptureUI.this.surfaceDestroyed(holder);
            previewUIDestroyed();
        }
    };

    private ShutterButton mShutterButton;
    private ImageView mVideoButton;
    private RenderOverlay mRenderOverlay;
    private FlashToggleButton mFlashButton;
    private CountDownView mCountDownView;
    private OneUICameraControls mCameraControls;
    private PieRenderer mPieRenderer;
    private ZoomRenderer mZoomRenderer;
    private Allocation mMonoDummyAllocation;
    private Allocation mMonoDummyOutputAllocation;
    private boolean mIsMonoDummyAllocationEverUsed = false;
    private boolean mIsTouchAF = false;

    private ViewGroup mFilterLayout;

    private View mFilterModeSwitcher;
    private View mSceneModeSwitcher;
    private View mFrontBackSwitcher;
    private ImageView mMakeupButton;
    private SeekBar mMakeupSeekBar;
    private SeekBar mBokehSeekBar;
    private TextView mBokehTipText;
    private RotateLayout mBokehTipRect;
    private View mMakeupSeekBarLayout;
    private View mSeekbarBody;
    private TextView mRecordingTimeView;
    private View mTimeLapseLabel;
    private RotateLayout mRecordingTimeRect;
    private PauseButton mPauseButton;
    private RotateImageView mMuteButton;
    private ImageView mSeekbarToggleButton;
    private View mProModeCloseButton;
    private RotateLayout mSceneModeLabelRect;
    private LinearLayout mSceneModeLabelView;
    private TextView mSceneModeName;
    private ImageView mExitBestMode;
    private ImageView mSceneModeLabelCloseIcon;
    private AlertDialog mSceneModeInstructionalDialog = null;

    private ImageView mCancelButton;
    private View mReviewCancelButton;
    private View mReviewDoneButton;
    private View mReviewRetakeButton;
    private View mReviewPlayButton;
    private FrameLayout mPreviewLayout;
    private ImageView mReviewImage;
    private int mDownSampleFactor = 4;
    private DecodeImageForReview mDecodeTaskForReview = null;

    int mPreviewWidth;
    int mPreviewHeight;
    private boolean mIsVideoUI = false;
    private boolean mIsSceneModeLabelClose = false;

    private void previewUIReady() {
        if ((getSurfaceHolder() != null && getSurfaceHolder().getSurface().isValid())) {
            mModule.onPreviewUIReady();
            if ((mIsVideoUI || mModule.getCurrentIntentMode() != CaptureModule.INTENT_MODE_NORMAL)
                    && mThumbnail != null) {
                mThumbnail.setVisibility(View.INVISIBLE);
                mThumbnail = null;
                getActivity().updateThumbnail(mThumbnail);
            } else if (!mIsVideoUI &&
                    mModule.getCurrentIntentMode() == CaptureModule.INTENT_MODE_NORMAL) {
                if (mThumbnail == null) {
                    mThumbnail = (ImageView) getRootView().findViewById(R.id.preview_thumb);
                }
                getActivity().updateThumbnail(mThumbnail);
            }
        }
    }

    private void previewUIDestroyed() {
        mModule.onPreviewUIDestroyed();
    }

    public TrackingFocusRenderer getTrackingFocusRenderer() {
        return mTrackingFocusRenderer;
    }

    @Override
    public @LayoutRes
    int getUILayout() {
        return R.layout.capture_module;
    }

    public CaptureUI(final CameraActivity activity, final CaptureModule module, View parent) {
        super(activity, parent);
        mModule = module;
        mSettingsManager = SettingsManager.getInstance();
        mSettingsManager.registerListener(this);
        mPreviewCover = getRootView().findViewById(R.id.preview_cover);
        getSurfaceView().addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right,
                                       int bottom, int oldLeft, int oldTop, int oldRight,
                                       int oldBottom) {
                int width = right - left;
                int height = bottom - top;
                if (mFaceView != null) {
                    mFaceView.onSurfaceTextureSizeChanged(width, height);
                }
            }
        });

        mSurfaceViewMono = (AutoFitSurfaceView) parent.findViewById(R.id.mdp_preview_content_mono);
        mSurfaceViewMono.setZOrderMediaOverlay(true);
        mSurfaceHolderMono = mSurfaceViewMono.getHolder();
        mSurfaceHolderMono.addCallback(callbackMono);

        mRenderOverlay = (RenderOverlay) parent.findViewById(R.id.render_overlay);
        mShutterButton = (ShutterButton) parent.findViewById(R.id.shutter_button);
        mVideoButton = (ImageView) parent.findViewById(R.id.video_button);
        mExitBestMode = (ImageView) parent.findViewById(R.id.exit_best_mode);
        mFilterModeSwitcher = parent.findViewById(R.id.filter_mode_switcher);
        mSceneModeSwitcher = parent.findViewById(R.id.scene_mode_switcher);
        mFrontBackSwitcher = parent.findViewById(R.id.front_back_switcher);
        mMakeupButton = (ImageView) parent.findViewById(R.id.ts_makeup_switcher);
        mMakeupSeekBarLayout = parent.findViewById(R.id.makeup_seekbar_layout);
        mSeekbarBody = parent.findViewById(R.id.seekbar_body);
        mSeekbarToggleButton = (ImageView) parent.findViewById(R.id.seekbar_toggle);
        mSeekbarToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSeekbarBody.getVisibility() == View.VISIBLE) {
                    mSeekbarBody.setVisibility(View.GONE);
                    mSeekbarToggleButton.setImageResource(R.drawable.seekbar_show);
                } else {
                    mSeekbarBody.setVisibility(View.VISIBLE);
                    mSeekbarToggleButton.setImageResource(R.drawable.seekbar_hide);
                }
            }
        });
        mMakeupSeekBar = (SeekBar) parent.findViewById(R.id.makeup_seekbar);
        mMakeupSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
                if ( progresValue != 0 ) {
                    int value = 10 + 9 * progresValue / 10;
                    mSettingsManager.setValue(SettingsManager.KEY_MAKEUP, value + "");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        mMakeupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (module != null && !module.isAllSessionClosed()) {
                    toggleMakeup();
                    updateMenus();
                }
            }
        });
        setMakeupButtonIcon();
        mFlashButton = (FlashToggleButton) parent.findViewById(R.id.flash_button);
        mProModeCloseButton = parent.findViewById(R.id.promode_close_button);
        mProModeCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSettingsManager.setValue(SettingsManager.KEY_SCENE_MODE, "" + SettingsManager.SCENE_MODE_AUTO_INT);
            }
        });
        mBokehSeekBar = (SeekBar) parent.findViewById(R.id.bokeh_seekbar);
        mBokehSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                module.setBokehBlurDegree(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                final SharedPreferences prefs =
                        PreferenceManager.getDefaultSharedPreferences(getActivity());
                prefs.edit().putInt(SettingsManager.KEY_BOKEH_BLUR_DEGREE, seekBar.getProgress())
                        .apply();
            }
        });
        mBokehTipText = getActivity().findViewById(R.id.bokeh_status);
        mBokehTipRect = (RotateLayout) getActivity().findViewById(R.id.bokeh_tip_rect);
        initFilterModeButton();
        initSceneModeButton();
        initSwitchCamera();
        initFlashButton();
        updateMenus();

        mRecordingTimeView = (TextView) parent.findViewById(R.id.recording_time);
        mRecordingTimeRect = (RotateLayout) parent.findViewById(R.id.recording_time_rect);
        mTimeLapseLabel = parent.findViewById(R.id.time_lapse_label);
        mPauseButton = (PauseButton) parent.findViewById(R.id.video_pause);
        mPauseButton.setOnPauseButtonListener(this);

        mMuteButton = (RotateImageView) parent.findViewById(R.id.mute_button);
        mMuteButton.setVisibility(View.VISIBLE);
        setMuteButtonResource(!mModule.isAudioMute());
        mMuteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isEnabled = !mModule.isAudioMute();
                mModule.setMute(isEnabled, true);
                setMuteButtonResource(!isEnabled);
            }
        });

        mExitBestMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SettingsManager.getInstance().setValueIndex(SettingsManager.KEY_SCENE_MODE,
                        AUTOMATIC_MODE);
            }
        });

        RotateImageView muteButton = (RotateImageView) parent.findViewById(R.id.mute_button);
        muteButton.setVisibility(View.GONE);

        mSceneModeLabelRect = (RotateLayout) parent.findViewById(R.id.scene_mode_label_rect);
        mSceneModeName = (TextView) parent.findViewById(R.id.scene_mode_label);
        mSceneModeLabelCloseIcon = (ImageView) parent.findViewById(R.id.scene_mode_label_close);
        mSceneModeLabelCloseIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsSceneModeLabelClose = true;
                mSceneModeLabelRect.setVisibility(View.GONE);
            }
        });

        mCameraControls = (OneUICameraControls) parent.findViewById(R.id.camera_controls);
        mFaceView = (Camera2FaceView) parent.findViewById(R.id.face_view);

        mCancelButton = (ImageView) getRootView().findViewById(R.id.cancel_button);
        final int intentMode = mModule.getCurrentIntentMode();
        if (intentMode != CaptureModule.INTENT_MODE_NORMAL) {
            mCameraControls.setIntentMode(intentMode);
            mCameraControls.setVideoMode(false);
            mCancelButton.setVisibility(View.VISIBLE);
            mReviewCancelButton = getRootView().findViewById(R.id.preview_btn_cancel);
            mReviewDoneButton = getRootView().findViewById(R.id.done_button);
            mReviewRetakeButton = getRootView().findViewById(R.id.preview_btn_retake);
            mReviewPlayButton = getRootView().findViewById(R.id.preview_play);
            mPreviewLayout = (FrameLayout)getRootView().findViewById(R.id.preview_of_intent);
            mReviewImage = (ImageView)getRootView().findViewById(R.id.preview_content);
            mReviewCancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    activity.setResultEx(Activity.RESULT_CANCELED, new Intent());
                    activity.finish();
                }
            });
            mReviewRetakeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mPreviewLayout.setVisibility(View.GONE);
                    mReviewImage.setImageBitmap(null);
                }
            });
            mReviewDoneButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (intentMode == CaptureModule.INTENT_MODE_CAPTURE) {
                        mModule.onCaptureDone();
                    } else if (intentMode == CaptureModule.INTENT_MODE_VIDEO) {
                        mModule.onRecordingDone(true);
                    }
                }
            });
            mReviewPlayButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mModule.startPlayVideoActivity();
                }
            });
            mCancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mModule.cancelCapture();
                }
            });
        }

        if (mPieRenderer == null) {
            mPieRenderer = new PieRenderer(activity);
            mRenderOverlay.addRenderer(mPieRenderer);
        }

        if (mZoomRenderer == null) {
            mZoomRenderer = new ZoomRenderer(activity);
            mZoomRenderer.setCameraControlHeight(getControlHeight());
            mRenderOverlay.addRenderer(mZoomRenderer);
        }

        if (mTrackingFocusRenderer == null) {
            mTrackingFocusRenderer = new TrackingFocusRenderer(activity, mModule, this);
            mRenderOverlay.addRenderer(mTrackingFocusRenderer);
        }
        if (mModule.isTrackingFocusSettingOn()) {
            mTrackingFocusRenderer.setVisible(true);
        } else {
            mTrackingFocusRenderer.setVisible(false);
        }

        if (mGestures == null) {
            // this will handle gesture disambiguation and dispatching
            mGestures = new PreviewGestures(activity, this, mZoomRenderer, mPieRenderer, mTrackingFocusRenderer);
            mRenderOverlay.setGestures(mGestures);
        }

        mGestures.setRenderOverlay(mRenderOverlay);
        mRenderOverlay.requestLayout();

        activity.setPreviewGestures(mGestures);
        mRecordingTimeRect.setVisibility(View.GONE);
    }

    protected void showCapturedImageForReview(byte[] jpegData, int orientation) {
        mDecodeTaskForReview = new CaptureUI.DecodeImageForReview(jpegData, orientation);
        mDecodeTaskForReview.execute();
        if (getCurrentIntentMode() != CaptureModule.INTENT_MODE_NORMAL) {
            if (mFilterMenuStatus == FILTER_MENU_ON) {
                removeFilterMenu(false);
            }
            mPreviewLayout.setVisibility(View.VISIBLE);
            CameraUtil.fadeIn(mReviewDoneButton);
            CameraUtil.fadeIn(mReviewRetakeButton);
        }
    }

    protected void showRecordVideoForReview(Bitmap preview) {
        if (getCurrentIntentMode() != CaptureModule.INTENT_MODE_NORMAL) {
            if (mFilterMenuStatus == FILTER_MENU_ON) {
                removeFilterMenu(false);
            }
            mReviewImage.setImageBitmap(preview);
            mPreviewLayout.setVisibility(View.VISIBLE);
            mReviewPlayButton.setVisibility(View.VISIBLE);
            CameraUtil.fadeIn(mReviewDoneButton);
            CameraUtil.fadeIn(mReviewRetakeButton);
        }
    }

    private int getCurrentIntentMode() {
        return mModule.getCurrentIntentMode();
    }

    private void toggleMakeup() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_MAKEUP);
        if (value != null && !mIsVideoUI) {
            if (value.equals("0")) {
                mSettingsManager.setValue(SettingsManager.KEY_MAKEUP, "50");
                mMakeupSeekBar.setProgress(50);
                mMakeupSeekBarLayout.setVisibility(View.VISIBLE);
                mSeekbarBody.setVisibility(View.VISIBLE);
                mSeekbarToggleButton.setImageResource(R.drawable.seekbar_hide);
            } else {
                mSettingsManager.setValue(SettingsManager.KEY_MAKEUP, "0");
                mMakeupSeekBar.setProgress(0);
                mMakeupSeekBarLayout.setVisibility(View.GONE);
            }
            setMakeupButtonIcon();
            mModule.restartSession(true);
        }
    }

    private void setMakeupButtonIcon() {
        final String value = mSettingsManager.getValue(SettingsManager.KEY_MAKEUP);
        getActivity().runOnUiThread(new Runnable() {
            public void run() {
                if (value != null && !value.equals("0")) {
                    mMakeupButton.setImageResource(R.drawable.beautify_on);
                    mMakeupSeekBarLayout.setVisibility(View.GONE);
                } else {
                    mMakeupButton.setImageResource(R.drawable.beautify);
                    mMakeupSeekBarLayout.setVisibility(View.GONE);
                }
            }
        });
    }

    public void onCameraOpened(List<Integer> cameraIds) {
        mGestures.setCaptureUI(this);
        mGestures.setZoomEnabled(mSettingsManager.isZoomSupported(cameraIds));
        initializeZoom(cameraIds);
    }

    public void reInitUI() {
        initSceneModeButton();
        initFilterModeButton();
        initFlashButton();
        setMakeupButtonIcon();
        showSceneModeLabel();
        updateMenus();
        if (mModule.isTrackingFocusSettingOn()) {
            mTrackingFocusRenderer.setVisible(false);
            mTrackingFocusRenderer.setVisible(true);
        } else {
            mTrackingFocusRenderer.setVisible(false);
        }

        if (mSurfaceViewMono != null) {
            if (mSettingsManager != null && mSettingsManager.getValue(SettingsManager.KEY_MONO_PREVIEW) != null
                    && mSettingsManager.getValue(SettingsManager.KEY_MONO_PREVIEW).equalsIgnoreCase("on")) {
                mSurfaceViewMono.setVisibility(View.VISIBLE);
            } else {
                mSurfaceViewMono.setVisibility(View.GONE);
            }
        }
    }

    public void initializeProMode(boolean promode) {
        mCameraControls.setProMode(promode);
        mCameraControls.setFixedFocus(mSettingsManager.isFixedFocus(mModule.getMainCameraId()));
        if (promode) {
            mVideoButton.setVisibility(View.INVISIBLE);
            mFlashButton.setVisibility(View.INVISIBLE);
        }
        else if (mModule.getCurrentIntentMode() == CaptureModule.INTENT_MODE_NORMAL)
            mVideoButton.setVisibility(View.VISIBLE);
    }

    public void initializeBokehMode(boolean bokehmode) {
        if (bokehmode) {
            final SharedPreferences prefs =
                    PreferenceManager.getDefaultSharedPreferences(getActivity());
            int progress = prefs.getInt(SettingsManager.KEY_BOKEH_BLUR_DEGREE, 50);
            mBokehSeekBar.setProgress(progress);
            mBokehSeekBar.setVisibility(View.VISIBLE);
            mVideoButton.setVisibility(View.INVISIBLE);
        } else {
            if (mBokehTipRect != null) {
                mBokehTipRect.setVisibility(View.INVISIBLE);
            }
            mBokehSeekBar.setVisibility(View.INVISIBLE);
        }
    }

    public TextView getBokehTipView() {
        return mBokehTipText;
    }

    public RotateLayout getBokehTipRct() {
        return mBokehTipRect;
    }

    // called from onResume but only the first time
    public void initializeFirstTime() {
        // Initialize shutter button.
        int intentMode = mModule.getCurrentIntentMode();
        if (intentMode == CaptureModule.INTENT_MODE_CAPTURE) {
            mVideoButton.setVisibility(View.INVISIBLE);
        } else if (intentMode == CaptureModule.INTENT_MODE_VIDEO) {
            mShutterButton.setVisibility(View.INVISIBLE);
        } else {
            mShutterButton.setVisibility(View.VISIBLE);
            mVideoButton.setVisibility(View.VISIBLE);
        }
        mShutterButton.setOnShutterButtonListener(mModule);
        mShutterButton.setImageResource(R.drawable.one_ui_shutter_anim);
        mShutterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doShutterAnimation();
            }
        });
        mVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelCountDown();
                mModule.onVideoButtonClick();
            }
        });
    }

    public void initializeZoom(List<Integer> ids) {
        if (!mSettingsManager.isZoomSupported(ids) || (mZoomRenderer == null))
            return;

        Float zoomMax = mSettingsManager.getMaxZoom(ids);
        mZoomRenderer.setZoomMax(zoomMax);
        mZoomRenderer.setZoom(1f);
        mZoomRenderer.setOnZoomChangeListener(new ZoomChangeListener());
    }

    public void enableGestures(boolean enable) {
        if (mGestures != null) {
            mGestures.setEnabled(enable);
        }
    }

    public boolean isPreviewMenuBeingShown() {
        return mFilterMenuStatus == FILTER_MENU_ON;
    }

    public void removeFilterMenu(boolean animate) {
        if (animate) {
            animateSlideOut(mFilterLayout);
        } else {
            mFilterMenuStatus = FILTER_MENU_NONE;
            if (mFilterLayout != null) {
                ((ViewGroup) getRootView()).removeView(mFilterLayout);
                mFilterLayout = null;
            }
        }
        updateMenus();
    }

    public void openSettingsMenu() {
        if (mPreviewLayout != null && mPreviewLayout.getVisibility() == View.VISIBLE) {
            return;
        }
        clearFocus();
        removeFilterMenu(false);
        Intent intent = new Intent(getActivity(), SettingsActivity.class);
        getActivity().startActivity(intent);
    }

    public void initSwitchCamera() {
        mFrontBackSwitcher.setVisibility(View.INVISIBLE);
        String value = mSettingsManager.getValue(SettingsManager.KEY_CAMERA_ID);
        Log.d(TAG,"value of KEY_CAMERA_ID is null? " + (value==null));
        if (value == null)
            return;

        mFrontBackSwitcher.setVisibility(View.VISIBLE);
        mFrontBackSwitcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mModule.getCameraModeSwitcherAllowed()) {
                    return;
                }
                mModule.setCameraModeSwitcherAllowed(false);
                removeFilterMenu(false);

                String value = mSettingsManager.getValue(SettingsManager.KEY_CAMERA_ID);
                if (value == null)
                    return;

                int index = mSettingsManager.getValueIndex(SettingsManager.KEY_CAMERA_ID);
                CharSequence[] entries = mSettingsManager.getEntries(SettingsManager.KEY_CAMERA_ID);
                do {
                    index = (index + 1) % entries.length;
                } while (entries[index] == null);
                mSettingsManager.setValueIndex(SettingsManager.KEY_CAMERA_ID, index);
            }
        });
    }

    public void initFlashButton() {
        mFlashButton.init(false);
        enableView(mFlashButton, SettingsManager.KEY_FLASH_MODE);
    }

    public void initSceneModeButton() {
        mSceneModeSwitcher.setVisibility(View.INVISIBLE);
        String value = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        if (value == null) return;
        mSceneModeSwitcher.setVisibility(View.VISIBLE);
        mSceneModeSwitcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearFocus();
                removeFilterMenu(false);
                Intent intent = new Intent(getActivity(), SceneModeActivity.class);
                intent.putExtra(CameraUtil.KEY_IS_SECURE_CAMERA, getActivity().isSecureCamera());
                getActivity().startActivity(intent);
            }
        });
    }

    public void initFilterModeButton() {
        mFilterModeSwitcher.setVisibility(View.INVISIBLE);
        String value = mSettingsManager.getValue(SettingsManager.KEY_COLOR_EFFECT);
        if (value == null) return;

        enableView(mFilterModeSwitcher, SettingsManager.KEY_COLOR_EFFECT);

        mFilterModeSwitcher.setVisibility(View.VISIBLE);
        mFilterModeSwitcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addFilterMode();
                adjustOrientation();
                updateMenus();
            }
        });
    }

    private void enableView(View view, String key) {
        Map<String, SettingsManager.Values> map = mSettingsManager.getValuesMap();
        SettingsManager.Values values = map.get(key);
        if (values != null) {
            boolean enabled = values.overriddenValue == null;
            view.setEnabled(enabled);
        }
    }

    public void showTimeLapseUI(boolean enable) {
        if (mTimeLapseLabel != null) {
            mTimeLapseLabel.setVisibility(enable ? View.VISIBLE : View.GONE);
        }
    }

    public void showRecordingUI(boolean recording, boolean highspeed) {
        if (recording) {
            if (highspeed) {
                mFlashButton.setVisibility(View.GONE);
            } else {
                mFlashButton.init(true);
            }
            mVideoButton.setImageResource(R.drawable.shutter_button_video_stop);
            mRecordingTimeView.setText("");
            mRecordingTimeRect.setVisibility(View.VISIBLE);
            mMuteButton.setVisibility(View.VISIBLE);
            setMuteButtonResource(!mModule.isAudioMute());
        } else {
            mFlashButton.setVisibility(View.VISIBLE);
            mFlashButton.init(false);
            mVideoButton.setImageResource(R.drawable.video_capture);
            mRecordingTimeRect.setVisibility(View.GONE);
            mMuteButton.setVisibility(View.INVISIBLE);
        }
    }

    private void setMuteButtonResource(boolean isUnMute) {
        if(isUnMute) {
            mMuteButton.setImageResource(R.drawable.ic_unmuted_button);
        } else {
            mMuteButton.setImageResource(R.drawable.ic_muted_button);
        }
    }

    private boolean needShowInstructional() {
        boolean needShow = true;
        final SharedPreferences pref = getActivity().getSharedPreferences(
                ComboPreferences.getGlobalSharedPreferencesName(getActivity()), Context.MODE_PRIVATE);
        int index = mSettingsManager.getValueIndex(SettingsManager.KEY_SCENE_MODE);
        if (index < 1) {
            needShow = false;
        } else {
            final String instructionalKey = SettingsManager.KEY_SCENE_MODE + "_" + index;
            needShow = pref.getBoolean(instructionalKey, false) ? false : true;
        }

        return needShow;

    }

    private void showSceneInstructionalDialog(int orientation) {
        int layoutId = R.layout.scene_mode_instructional;
        if (orientation == 90 || orientation == 270) {
            layoutId = R.layout.scene_mode_instructional_landscape;
        }
        LayoutInflater inflater =
                (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(layoutId, null);

        final int index = mSettingsManager.getValueIndex(SettingsManager.KEY_SCENE_MODE);
        TextView name = (TextView) view.findViewById(R.id.scene_mode_name);
        CharSequence sceneModeNameArray[] =
                mSettingsManager.getEntries(SettingsManager.KEY_SCENE_MODE);
        name.setText(sceneModeNameArray[index]);

        ImageView icon = (ImageView) view.findViewById(R.id.scene_mode_icon);
        int[] resId = mSettingsManager.getResource(SettingsManager.KEY_SCEND_MODE_INSTRUCTIONAL,
                SettingsManager.RESOURCE_TYPE_THUMBNAIL);
        icon.setImageResource(resId[index]);

        TextView instructional = (TextView) view.findViewById(R.id.scene_mode_instructional);
        CharSequence instructionalArray[] =
                mSettingsManager.getEntries(SettingsManager.KEY_SCEND_MODE_INSTRUCTIONAL);
        if (instructionalArray[index].length() == 0) {
            //For now, not all scene mode has instructional
            return;
        }
        instructional.setText(instructionalArray[index]);

        final CheckBox remember = (CheckBox) view.findViewById(R.id.remember_selected);
        Button ok = (Button) view.findViewById(R.id.scene_mode_instructional_ok);
        ok.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {

                if (remember.isChecked()) {
                    final SharedPreferences pref = getActivity().getSharedPreferences(
                            ComboPreferences.getGlobalSharedPreferencesName(getActivity()),
                            Context.MODE_PRIVATE);

                    String instructionalKey = SettingsManager.KEY_SCENE_MODE + "_" + index;
                    SharedPreferences.Editor editor = pref.edit();
                    editor.putBoolean(instructionalKey, true);
                    editor.commit();
                }
                mSceneModeInstructionalDialog.dismiss();
                mSceneModeInstructionalDialog = null;
            }
        });

        mSceneModeInstructionalDialog =
                new AlertDialog.Builder(getActivity(), AlertDialog.THEME_HOLO_LIGHT)
                        .setView(view).create();
        try {
            mSceneModeInstructionalDialog.show();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        if (orientation != 0) {
            rotationSceneModeInstructionalDialog(view, orientation);
        }
    }

    private int getScreenWidth() {
        DisplayMetrics metric = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(metric);
        return metric.widthPixels < metric.heightPixels ? metric.widthPixels : metric.heightPixels;
    }

    private void rotationSceneModeInstructionalDialog(View view, int orientation) {
        view.setRotation(-orientation);
        int screenWidth = getScreenWidth();
        int dialogSize = screenWidth * 9 / 10;
        Window dialogWindow = mSceneModeInstructionalDialog.getWindow();
        WindowManager.LayoutParams lp = dialogWindow.getAttributes();
        dialogWindow.setGravity(Gravity.CENTER);
        lp.width = lp.height = dialogSize;
        dialogWindow.setAttributes(lp);
        RelativeLayout layout = (RelativeLayout) view.findViewById(R.id.mode_layout_rect);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dialogSize, dialogSize);
        layout.setLayoutParams(params);
    }

    private void showSceneModeLabel() {
        mIsSceneModeLabelClose = false;
        int index = mSettingsManager.getValueIndex(SettingsManager.KEY_SCENE_MODE);
        CharSequence sceneModeNameArray[] = mSettingsManager.getEntries(SettingsManager.KEY_SCENE_MODE);
        if (index > 0 && index < sceneModeNameArray.length) {
            mSceneModeName.setText(sceneModeNameArray[index]);
            mSceneModeLabelRect.setVisibility(View.VISIBLE);
            mExitBestMode.setVisibility(View.VISIBLE);
        } else {
            mSceneModeLabelRect.setVisibility(View.GONE);
            mExitBestMode.setVisibility(View.GONE);
        }
    }


    public void resetTrackingFocus() {
        if (mModule.isTrackingFocusSettingOn()) {
            mTrackingFocusRenderer.setVisible(false);
            mTrackingFocusRenderer.setVisible(true);
        }
    }

    public void hideUIwhileRecording() {
        mCameraControls.setVideoMode(true);
        mSceneModeLabelRect.setVisibility(View.INVISIBLE);
        mFrontBackSwitcher.setVisibility(View.INVISIBLE);
        mFilterModeSwitcher.setVisibility(View.INVISIBLE);
        mSceneModeSwitcher.setVisibility(View.INVISIBLE);
        String value = mSettingsManager.getValue(SettingsManager.KEY_MAKEUP);
        if (value != null && value.equals("0")) {
            mMakeupButton.setVisibility(View.GONE);
        }
        mIsVideoUI = true;
        mPauseButton.setVisibility(View.VISIBLE);
    }

    public void showUIafterRecording() {
        mCameraControls.setVideoMode(false);
        mFrontBackSwitcher.setVisibility(View.VISIBLE);
        mFilterModeSwitcher.setVisibility(View.VISIBLE);
        mSceneModeSwitcher.setVisibility(View.VISIBLE);
        mMakeupButton.setVisibility(View.GONE);
        mIsVideoUI = false;
        mPauseButton.setVisibility(View.INVISIBLE);
        //exit recording mode needs to refresh scene mode label.
        showSceneModeLabel();
    }

    public void addFilterMode() {
        if (mSettingsManager.getValue(SettingsManager.KEY_COLOR_EFFECT) == null)
            return;

        int rotation = CameraUtil.getDisplayRotation(getActivity());
        boolean mIsDefaultToPortrait = CameraUtil.isDefaultToPortrait(getActivity());
        if (!mIsDefaultToPortrait) {
            rotation = (rotation + 90) % 360;
        }
        WindowManager wm = (WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        CharSequence[] entries = mSettingsManager.getEntries(SettingsManager.KEY_COLOR_EFFECT);

        Resources r = getActivity().getResources();
        int height = (int) (r.getDimension(R.dimen.filter_mode_height) + 2
                * r.getDimension(R.dimen.filter_mode_padding) + 1);
        int width = (int) (r.getDimension(R.dimen.filter_mode_width) + 2
                * r.getDimension(R.dimen.filter_mode_padding) + 1);

        int gridRes;
        boolean portrait = (rotation == 0) || (rotation == 180);
        int size = height;
        if (!portrait) {
            gridRes = R.layout.vertical_grid;
            size = width;
        } else {
            gridRes = R.layout.horiz_grid;
        }

        int[] thumbnails = mSettingsManager.getResource(SettingsManager.KEY_COLOR_EFFECT,
                SettingsManager.RESOURCE_TYPE_THUMBNAIL);
        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        FrameLayout gridOuterLayout = (FrameLayout) inflater.inflate(
                gridRes, null, false);
        gridOuterLayout.setBackgroundColor(android.R.color.transparent);
        removeFilterMenu(false);
        mFilterMenuStatus = FILTER_MENU_ON;
        mFilterLayout = new LinearLayout(getActivity());

        ViewGroup.LayoutParams params = null;
        if (!portrait) {
            params = new ViewGroup.LayoutParams(size, FrameLayout.LayoutParams.MATCH_PARENT);
            mFilterLayout.setLayoutParams(params);
            ((ViewGroup) getRootView()).addView(mFilterLayout);
        } else {
            params = new ViewGroup.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, size);
            mFilterLayout.setLayoutParams(params);
            ((ViewGroup) getRootView()).addView(mFilterLayout);
            mFilterLayout.setY(display.getHeight() - 2 * size);
        }
        gridOuterLayout.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams
                .MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout gridLayout = (LinearLayout) gridOuterLayout.findViewById(R.id.layout);
        final View[] views = new View[entries.length];

        int init = mSettingsManager.getValueIndex(SettingsManager.KEY_COLOR_EFFECT);
        for (int i = 0; i < entries.length; i++) {
            RotateLayout filterBox = (RotateLayout) inflater.inflate(
                    R.layout.filter_mode_view, null, false);
            ImageView imageView = (ImageView) filterBox.findViewById(R.id.image);
            final int j = i;

            filterBox.setOnTouchListener(new View.OnTouchListener() {
                private long startTime;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        startTime = System.currentTimeMillis();
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        if (System.currentTimeMillis() - startTime < CLICK_THRESHOLD) {
                            mSettingsManager.setValueIndex(SettingsManager
                                    .KEY_COLOR_EFFECT, j);
                            for (View v1 : views) {
                                v1.setBackground(null);
                            }
                            ImageView image = (ImageView) v.findViewById(R.id.image);
                            image.setBackgroundColor(HIGHLIGHT_COLOR);
                        }
                    }
                    return true;
                }
            });

            views[j] = imageView;
            if (i == init)
                imageView.setBackgroundColor(HIGHLIGHT_COLOR);
            TextView label = (TextView) filterBox.findViewById(R.id.label);

            imageView.setImageResource(thumbnails[i]);
            label.setText(entries[i]);
            gridLayout.addView(filterBox);
        }
        mFilterLayout.addView(gridOuterLayout);
    }

    public void removeAndCleanUpFilterMenu() {
        removeFilterMenu(false);
        cleanUpMenus();
    }

    public void animateFadeIn(View v) {
        ViewPropertyAnimator vp = v.animate();
        vp.alpha(0.85f).setDuration(ANIMATION_DURATION);
        vp.start();
    }

    private void animateSlideOut(final View v) {
        if (v == null || mFilterMenuStatus == FILTER_MENU_IN_ANIMATION)
            return;
        mFilterMenuStatus = FILTER_MENU_IN_ANIMATION;

        ViewPropertyAnimator vp = v.animate();
        vp.translationXBy(-v.getWidth()).setDuration(ANIMATION_DURATION);
        vp.setListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                removeAndCleanUpFilterMenu();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                removeAndCleanUpFilterMenu();
            }
        });
        vp.start();
    }

    public void animateSlideIn(View v, int delta, boolean forcePortrait) {
        int orientation = getOrientation();
        if (!forcePortrait)
            orientation = 0;

        ViewPropertyAnimator vp = v.animate();
        float dest;
        switch (orientation) {
            case 0:
                dest = v.getX();
                v.setX(dest - delta);
                vp.translationX(dest);
                break;
            case 90:
                dest = v.getY();
                v.setY(dest + delta);
                vp.translationY(dest);
                break;
            case 180:
                dest = v.getX();
                v.setX(dest + delta);
                vp.translationX(dest);
                break;
            case 270:
                dest = v.getY();
                v.setY(dest - delta);
                vp.translationY(dest);
                break;
        }
        vp.setDuration(ANIMATION_DURATION).start();
    }

    public void hideUIWhileCountDown() {
        hideCameraControls(true);
        mGestures.setZoomOnly(true);
    }

    public void showUIAfterCountDown() {
        hideCameraControls(false);
        mGestures.setZoomOnly(false);
    }

    public void hideCameraControls(boolean hide) {
        final int status = (hide) ? View.INVISIBLE : View.VISIBLE;
        if (mFlashButton != null) {
            mFlashButton.setVisibility(
                    hide || !mSettingsManager.isFlashSupported(mModule.getMainCameraId()) ?
                    View.INVISIBLE : View.VISIBLE);
        }
        if (mFrontBackSwitcher != null) mFrontBackSwitcher.setVisibility(status);
        if (mSceneModeSwitcher != null) mSceneModeSwitcher.setVisibility(status);
        if (mFilterModeSwitcher != null) mFilterModeSwitcher.setVisibility(status);
        if (mFilterModeSwitcher != null) mFilterModeSwitcher.setVisibility(status);
        if (mMakeupButton != null) mMakeupButton.setVisibility(View.GONE);
    }

    public void initializeControlByIntent() {
        mThumbnail = (ImageView) getRootView().findViewById(R.id.preview_thumb);
        mThumbnail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mModule.isTakingPicture() && !mModule.isRecordingVideo())
                    getActivity().gotoGallery();
            }
        });
        if (mModule.getCurrentIntentMode() != CaptureModule.INTENT_MODE_NORMAL) {
            mCameraControls.setIntentMode(mModule.getCurrentIntentMode());
        }
    }

    public void doShutterAnimation() {
        AnimationDrawable frameAnimation = (AnimationDrawable) mShutterButton.getDrawable();
        frameAnimation.stop();
        frameAnimation.start();
    }

    @Override
    public void showUI() {
        super.showUI();
        if (!arePreviewControlsVisible()) {
            mPieRenderer.setBlockFocus(false);
            mCameraControls.showUI();
        }
    }

    @Override
    public void hideUI() {
        if (arePreviewControlsVisible()) {
            mPieRenderer.setBlockFocus(true);
            mCameraControls.hideUI();
        }
    }

    public void cleanUpMenus() {
        showUI();
        updateMenus();
        getActivity().setSystemBarsVisibility(false);
    }

    public void updateMenus() {
        boolean enableMakeupMenu = true;
        boolean enableFilterMenu = true;
        boolean enableSceneMenu = true;
        String makeupValue = mSettingsManager.getValue(SettingsManager.KEY_MAKEUP);
        int colorEffect = mSettingsManager.getValueIndex(SettingsManager.KEY_COLOR_EFFECT);
        String sceneMode = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        if (makeupValue != null && !makeupValue.equals("0")) {
            enableSceneMenu = false;
            enableFilterMenu = false;
        } else if (colorEffect != 0 || mFilterMenuStatus == FILTER_MENU_ON) {
            enableSceneMenu = false;
            enableMakeupMenu = false;
        } else if (sceneMode != null && !sceneMode.equals("0")) {
            enableMakeupMenu = false;
            enableFilterMenu = false;
        }
        mMakeupButton.setEnabled(enableMakeupMenu);
        if(!BeautificationFilter.isSupportedStatic()) {
            mMakeupButton.setVisibility(View.GONE);
        }
        mFilterModeSwitcher.setEnabled(enableFilterMenu);
        mSceneModeSwitcher.setEnabled(enableSceneMenu);
    }

    public void onOrientationChanged() {
    }

    /**
     * Enables or disables the shutter button.
     */
    public void enableShutter(boolean enabled) {
        if (mShutterButton != null) {
            mShutterButton.setEnabled(enabled);
        }
    }

    public boolean isShutterEnabled() {
        return mShutterButton.isEnabled();
    }

    /**
     * Enables or disables the video button.
     */
    public void enableVideo(boolean enabled) {
        if (mVideoButton != null) {
            mVideoButton.setEnabled(enabled);
        }
    }

    private boolean handleBackKeyOnMenu() {
        if (mFilterMenuStatus == FILTER_MENU_ON) {
            removeFilterMenu(true);
            return true;
        }
        return false;
    }

    public boolean onBackPressed() {
        if (handleBackKeyOnMenu()) return true;
        if (mPieRenderer != null && mPieRenderer.showsItems()) {
            mPieRenderer.hide();
            return true;
        }

        if (!mModule.isCameraIdle()) {
            // ignore backs while we're taking a picture
            return true;
        }
        return false;
    }

    private class MonoDummyListener implements Allocation.OnBufferAvailableListener {
        ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic;

        public MonoDummyListener(ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic) {
            this.yuvToRgbIntrinsic = yuvToRgbIntrinsic;
        }

        @Override
        public void onBufferAvailable(Allocation a) {
            if (mMonoDummyAllocation != null) {
                mMonoDummyAllocation.ioReceive();
                mIsMonoDummyAllocationEverUsed = true;
                if (mSurfaceViewMono.getVisibility() == View.VISIBLE) {
                    try {
                        yuvToRgbIntrinsic.forEach(mMonoDummyOutputAllocation);
                        mMonoDummyOutputAllocation.ioSend();
                    } catch (Exception e) {
                        Log.e(TAG, e.toString());
                    }
                }
            }
        }
    }

    public Surface getMonoDummySurface() {
        if (mMonoDummyAllocation == null) {
            RenderScript rs = RenderScript.create(getActivity());
            Type.Builder yuvTypeBuilder = new Type.Builder(rs, Element.YUV(rs));
            yuvTypeBuilder.setX(mPreviewWidth);
            yuvTypeBuilder.setY(mPreviewHeight);
            yuvTypeBuilder.setYuvFormat(ImageFormat.YUV_420_888);
            mMonoDummyAllocation = Allocation.createTyped(rs, yuvTypeBuilder.create(), Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);
            ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.RGBA_8888(rs));
            yuvToRgbIntrinsic.setInput(mMonoDummyAllocation);

            if (mSettingsManager.getValue(SettingsManager.KEY_MONO_PREVIEW).equalsIgnoreCase("on")) {
                Type.Builder rgbTypeBuilder = new Type.Builder(rs, Element.RGBA_8888(rs));
                rgbTypeBuilder.setX(mPreviewWidth);
                rgbTypeBuilder.setY(mPreviewHeight);
                mMonoDummyOutputAllocation = Allocation.createTyped(rs, rgbTypeBuilder.create(), Allocation.USAGE_SCRIPT | Allocation.USAGE_IO_OUTPUT);
                mMonoDummyOutputAllocation.setSurface(mSurfaceHolderMono.getSurface());
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        mSurfaceHolderMono.setFixedSize(mPreviewWidth, mPreviewHeight);
                        mSurfaceViewMono.setVisibility(View.VISIBLE);
                    }
                });
            }
            mMonoDummyAllocation.setOnBufferAvailableListener(new MonoDummyListener(yuvToRgbIntrinsic));

            mIsMonoDummyAllocationEverUsed = false;
        }
        return mMonoDummyAllocation.getSurface();
    }

    public void showPreviewCover() {
        mPreviewCover.setVisibility(View.VISIBLE);
    }

    public void hidePreviewCover() {
        // Hide the preview cover if need.
        if (mPreviewCover.getVisibility() != View.GONE) {
            mPreviewCover.setVisibility(View.GONE);
        }
    }

    private void initializeCountDown() {
        getActivity().getLayoutInflater().inflate(R.layout.count_down_to_capture,
                (ViewGroup) getRootView(), true);
        mCountDownView = (CountDownView) (getRootView().findViewById(R.id.count_down_to_capture));
        mCountDownView.setCountDownFinishedListener((CountDownView.OnCountDownFinishedListener) mModule);
        mCountDownView.bringToFront();
        mCountDownView.setOrientation(mOrientation);
    }

    public boolean isCountingDown() {
        return mCountDownView != null && mCountDownView.isCountingDown();
    }

    public void cancelCountDown() {
        if (mCountDownView == null) return;
        mCountDownView.cancelCountDown();
        showUIAfterCountDown();
    }

    public void initCountDownView() {
        if (mCountDownView == null) initializeCountDown();
    }

    public void releaseSoundPool() {
        if (mCountDownView != null) {
            mCountDownView.releaseSoundPool();
            mCountDownView = null;
        }
    }

    public void startCountDown(int sec, boolean playSound) {
        mCountDownView.startCountDown(sec, playSound);
        hideUIWhileCountDown();
    }

    public void onPause() {
        cancelCountDown();
        collapseCameraControls();

        if (mFaceView != null) mFaceView.clear();
        if (mTrackingFocusRenderer != null) {
            mTrackingFocusRenderer.setVisible(false);
        }
        if (mMonoDummyAllocation != null && mIsMonoDummyAllocationEverUsed) {
            mMonoDummyAllocation.setOnBufferAvailableListener(null);
            mMonoDummyAllocation.destroy();
            mMonoDummyAllocation = null;
        }
        if (mMonoDummyOutputAllocation != null && mIsMonoDummyAllocationEverUsed) {
            mMonoDummyOutputAllocation.destroy();
            mMonoDummyOutputAllocation = null;
        }
        mSurfaceViewMono.setVisibility(View.GONE);
    }

    public boolean collapseCameraControls() {
        // Remove all the popups/dialog boxes
        boolean ret = false;
        mCameraControls.showRefocusToast(false);
        return ret;
    }

    public void showRefocusToast(boolean show) {
        mCameraControls.showRefocusToast(show);
    }

    private FocusIndicator getFocusIndicator() {
        if (mModule.isTrackingFocusSettingOn()) {
            if (mPieRenderer != null) {
                mPieRenderer.clear();
            }
            return mTrackingFocusRenderer;
        }

        FocusIndicator focusIndicator;
        if (mFaceView != null && mFaceView.faceExists() && !mIsTouchAF) {
            if (mPieRenderer != null) {
                mPieRenderer.clear();
            }
            focusIndicator = mFaceView;
        } else {
            focusIndicator = mPieRenderer;
        }

        return focusIndicator;
    }

    @Override
    public boolean hasFaces() {
        return (mFaceView != null && mFaceView.faceExists());
    }

    public void clearFaces() {
        if (mFaceView != null) mFaceView.clear();
    }

    @Override
    public void clearFocus() {
        FocusIndicator indicator = getFocusIndicator();
        if (indicator != null) indicator.clear();
        mIsTouchAF = false;
    }

    @Override
    public void setFocusPosition(int x, int y) {
        mPieRenderer.setFocus(x, y);
        mIsTouchAF = true;
    }

    @Override
    public void onFocusStarted() {
        FocusIndicator indicator = getFocusIndicator();
        if (indicator != null) indicator.showStart();
    }

    @Override
    public void onFocusSucceeded(boolean timeout) {
        FocusIndicator indicator = getFocusIndicator();
        if (indicator != null) indicator.showSuccess(timeout);
    }

    @Override
    public void onFocusFailed(boolean timeOut) {
        FocusIndicator indicator = getFocusIndicator();
        if (indicator != null) indicator.showFail(timeOut);

    }

    @Override
    public void pauseFaceDetection() {

    }

    @Override
    public void resumeFaceDetection() {
    }

    public void onStartFaceDetection(int orientation, boolean mirror, Rect cameraBound,
                                     Rect originalCameraBound) {
        mFaceView.setBlockDraw(false);
        mFaceView.clear();
        mFaceView.setVisibility(View.VISIBLE);
        mFaceView.setDisplayOrientation(orientation);
        mFaceView.setMirror(mirror);
        mFaceView.setCameraBound(cameraBound);
        mFaceView.setOriginalCameraBound(originalCameraBound);
        mFaceView.setZoom(mModule.getZoomValue());
        mFaceView.resume();
    }

    public void updateFaceViewCameraBound(Rect cameraBound) {
        mFaceView.setCameraBound(cameraBound);
        mFaceView.setZoom(mModule.getZoomValue());
    }

    public void onStopFaceDetection() {
        if (mFaceView != null) {
            mFaceView.setBlockDraw(true);
            mFaceView.clear();
        }
    }

    @Override
    public void onFaceDetection(Face[] faces, CameraManager.CameraProxy camera) {
    }

    public void onFaceDetection(android.hardware.camera2.params.Face[] faces,
                                ExtendedFace[] extendedFaces) {
        mFaceView.setFaces(faces, extendedFaces);
    }

    public void adjustOrientation() {
        setOrientation(mOrientation, true);
    }

    public void setOrientation(int orientation, boolean animation) {
        mOrientation = orientation;
        mCameraControls.setOrientation(orientation, animation);
        if (mFilterLayout != null) {
            ViewGroup vg = (ViewGroup) mFilterLayout.getChildAt(0);
            if (vg != null)
                vg = (ViewGroup) vg.getChildAt(0);
            if (vg != null) {
                for (int i = vg.getChildCount() - 1; i >= 0; --i) {
                    RotateLayout l = (RotateLayout) vg.getChildAt(i);
                    l.setOrientation(orientation, animation);
                }
            }
        }
        if (mRecordingTimeRect != null) {
            mRecordingTimeView.setRotation(-orientation);
        }
        if (mFaceView != null) {
            mFaceView.setDisplayRotation(orientation);
        }
        if (mCountDownView != null)
            mCountDownView.setOrientation(orientation);
        RotateTextToast.setOrientation(orientation);
        if (mZoomRenderer != null) {
            mZoomRenderer.setOrientation(orientation);
        }

        if (mSceneModeLabelRect != null) {
            if (orientation == 180) {
                mSceneModeName.setRotation(180);
                mSceneModeLabelCloseIcon.setRotation(180);
                mSceneModeLabelRect.setOrientation(0, false);
            } else {
                mSceneModeName.setRotation(0);
                mSceneModeLabelCloseIcon.setRotation(0);
                mSceneModeLabelRect.setOrientation(orientation, false);
            }
        }

        if (mBokehTipRect != null) {
            if (orientation == 180) {
                mBokehTipText.setRotation(180);
                mBokehTipRect.setOrientation(0, false);
            } else {
                mBokehTipText.setRotation(0);
                mBokehTipRect.setOrientation(orientation, false);
            }
        }

        if (mSceneModeInstructionalDialog != null && mSceneModeInstructionalDialog.isShowing()) {
            mSceneModeInstructionalDialog.dismiss();
            mSceneModeInstructionalDialog = null;
            showSceneInstructionalDialog(orientation);
        }
    }

    public int getOrientation() {
        return mOrientation;
    }

    @Override
    public void onSingleTapUp(View view, int x, int y) {
        mModule.onSingleTapUp(view, x, y);
    }

    public boolean isOverControlRegion(int[] xy) {
        int x = xy[0];
        int y = xy[1];
        return mCameraControls.isControlRegion(x, y);
    }

    public boolean isOverSurfaceView(int[] xy) {
        int x = xy[0];
        int y = xy[1];
        int[] surfaceViewLocation = new int[2];
        getSurfaceView().getLocationInWindow(surfaceViewLocation);
        int surfaceViewX = surfaceViewLocation[0];
        int surfaceViewY = surfaceViewLocation[1];
        xy[0] = x - surfaceViewX;
        xy[1] = y - surfaceViewY;
        return (x > surfaceViewX) && (x < surfaceViewX + getSurfaceViewSize().x)
                && (y > surfaceViewY) && (y < surfaceViewY + getSurfaceViewSize().y);
    }

    public void onPreviewFocusChanged(boolean previewFocused) {
        if (previewFocused) {
            showUI();
        } else {
            hideUI();
        }
        if (mFaceView != null) {
            mFaceView.setBlockDraw(!previewFocused);
        }
        if (mGestures != null) {
            mGestures.setEnabled(previewFocused);
        }
        if (mRenderOverlay != null) {
            // this can not happen in capture mode
            mRenderOverlay.setVisibility(previewFocused ? View.VISIBLE : View.GONE);
        }
        if (mPieRenderer != null) {
            mPieRenderer.setBlockFocus(!previewFocused);
        }
        if (!previewFocused && mCountDownView != null) mCountDownView.cancelCountDown();
    }

    public boolean isShutterPressed() {
        return mShutterButton.isPressed();
    }

    public void pressShutterButton() {
        if (mShutterButton.isInTouchMode()) {
            mShutterButton.requestFocusFromTouch();
        } else {
            mShutterButton.requestFocus();
        }
        mShutterButton.setPressed(true);
    }

    public void setRecordingTime(String text) {
        mRecordingTimeView.setText(text);
    }

    public void setRecordingTimeTextColor(int color) {
        mRecordingTimeView.setTextColor(color);
    }

    public void resetPauseButton() {
        mRecordingTimeView.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_recording_indicator, 0, 0, 0);
        mPauseButton.setPaused(false);
    }

    @Override
    public void onButtonPause() {
        mRecordingTimeView.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_pausing_indicator, 0, 0, 0);
        mModule.onButtonPause();
    }

    @Override
    public void onButtonContinue() {
        mRecordingTimeView.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_recording_indicator, 0, 0, 0);
        mModule.onButtonContinue();
    }

    @Override
    public void onSettingsChanged(List<SettingsManager.SettingState> settings) {
        for (SettingsManager.SettingState state : settings) {
            if (state.key.equals(SettingsManager.KEY_COLOR_EFFECT)) {
                enableView(mFilterModeSwitcher, SettingsManager.KEY_COLOR_EFFECT);
            } else if (state.key.equals(SettingsManager.KEY_SCENE_MODE)) {
                String value = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
                if (value.equals("104")) {//panorama
                    mSceneModeLabelRect.setVisibility(View.GONE);
                } else {
                    if (needShowInstructional()) {
                        showSceneInstructionalDialog(mOrientation);
                    }
                    showSceneModeLabel();
                }
            } else if (state.key.equals(SettingsManager.KEY_FLASH_MODE)) {
                enableView(mFlashButton, SettingsManager.KEY_FLASH_MODE);
            }else if (state.key.equals(SettingsManager.KEY_FOCUS_DISTANCE)) {
                if (mPieRenderer != null)
                    mPieRenderer.setVisible(false);
            }
        }
    }

    public void startSelfieFlash() {
        if (mSelfieView == null)
            mSelfieView = (SelfieFlashView) (getRootView().findViewById(R.id.selfie_flash));
        mSelfieView.bringToFront();
        mSelfieView.open();
        mScreenBrightness = setScreenBrightness(1F);
    }

    public void stopSelfieFlash() {
        if (mSelfieView == null)
            mSelfieView = (SelfieFlashView) (getRootView().findViewById(R.id.selfie_flash));
        mSelfieView.close();
        if (mScreenBrightness != 0.0f)
            setScreenBrightness(mScreenBrightness);
    }

    private float setScreenBrightness(float brightness) {
        float originalBrightness;
        Window window = getActivity().getWindow();
        WindowManager.LayoutParams layout = window.getAttributes();
        originalBrightness = layout.screenBrightness;
        layout.screenBrightness = brightness;
        window.setAttributes(layout);
        return originalBrightness;
    }

    @Override
    public void showSurfaceView() {
        super.showSurfaceView();
        getSurfaceView().getHolder().setFixedSize(mPreviewWidth, mPreviewHeight);
        ((AutoFitSurfaceView) getSurfaceView()).setAspectRatio(mPreviewHeight, mPreviewWidth);
        mIsVideoUI = false;
    }

    public boolean setPreviewSize(int width, int height) {
        Log.d(TAG, "setPreviewSize " + width + " " + height);
        boolean changed = (width != mPreviewWidth) || (height != mPreviewHeight);
        mPreviewWidth = width;
        mPreviewHeight = height;
        if (changed) {
            showSurfaceView();
        }
        return changed;
    }

    private class ZoomChangeListener implements ZoomRenderer.OnZoomChangedListener {
        @Override
        public void onZoomValueChanged(float mZoomValue) {
            mModule.onZoomChanged(mZoomValue);
            if (mZoomRenderer != null) {
                mZoomRenderer.setZoom(mZoomValue);
            }
        }

        @Override
        public void onZoomStart() {
            if (mPieRenderer != null) {
                mPieRenderer.hide();
                mPieRenderer.setBlockFocus(true);
            }
        }

        @Override
        public void onZoomEnd() {
            if (mPieRenderer != null) {
                mPieRenderer.setBlockFocus(false);
            }
        }

        @Override
        public void onZoomValueChanged(int index) {

        }
    }

    private class DecodeTask extends AsyncTask<Void, Void, Bitmap> {
        private final byte[] mData;
        private int mOrientation;

        public DecodeTask(byte[] data, int orientation) {
            mData = data;
            mOrientation = orientation;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            Bitmap bitmap = CameraUtil.downSample(mData, mDownSampleFactor);
            // Decode image in background.
            if ((mOrientation != 0) && (bitmap != null)) {
                Matrix m = new Matrix();
                m.preRotate(mOrientation);
                return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m,
                        false);
            }
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
        }
    }

    private class DecodeImageForReview extends CaptureUI.DecodeTask {
        public DecodeImageForReview(byte[] data, int orientation) {
            super(data, orientation);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled()) {
                return;
            }
            mReviewImage.setImageBitmap(bitmap);
            mReviewImage.setVisibility(View.VISIBLE);
            mDecodeTaskForReview = null;
        }
    }

    public ImageView getVideoButton() {
        return mVideoButton;
    }

    public int getCurrentProMode() {
        return mCameraControls.getPromode();
    }
}
