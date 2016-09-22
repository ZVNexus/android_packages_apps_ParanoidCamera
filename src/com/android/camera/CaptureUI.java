/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera.Face;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.camera.ui.AutoFitSurfaceView;
import com.android.camera.ui.Camera2FaceView;
import com.android.camera.ui.CameraControls;
import com.android.camera.ui.OneUICameraControls;
import com.android.camera.ui.CountDownView;
import com.android.camera.ui.FlashToggleButton;
import com.android.camera.ui.FocusIndicator;
import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.RenderOverlay;
import com.android.camera.ui.RotateImageView;
import com.android.camera.ui.RotateLayout;
import com.android.camera.ui.RotateTextToast;
import com.android.camera.ui.SelfieFlashView;
import com.android.camera.ui.TrackingFocusRenderer;
import com.android.camera.ui.ZoomRenderer;
import com.android.camera.util.CameraUtil;

import org.codeaurora.snapcam.R;

import java.util.List;
import java.util.Locale;

public class CaptureUI implements FocusOverlayManager.FocusUI,
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
    private CameraActivity mActivity;
    private View mRootView;
    private View mPreviewCover;
    private CaptureModule mModule;
    private AutoFitSurfaceView mSurfaceView;
    private AutoFitSurfaceView mSurfaceViewMono;
    private SurfaceHolder mSurfaceHolder;
    private SurfaceHolder mSurfaceHolderMono;
    private int mOrientation;
    private int mFilterMenuStatus;
    private PreviewGestures mGestures;
    private boolean mUIhidden = false;
    private SettingsManager mSettingsManager;
    private TrackingFocusRenderer mTrackingFocusRenderer;
    private ImageView mThumbnail;
    private Camera2FaceView mFaceView;
    private Point mDisplaySize = new Point();
    private SelfieFlashView mSelfieView;
    private float mScreenBrightness = 0.0f;

    private SurfaceHolder.Callback callbackMono = new SurfaceHolder.Callback() {
        // SurfaceHolder callbacks
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            mSurfaceHolderMono = holder;
            if(mMonoDummyOutputAllocation != null) {
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
            Log.v(TAG, "surfaceChanged: width =" + width + ", height = " + height);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.v(TAG, "surfaceCreated");
            mSurfaceHolder = holder;
            previewUIReady();
            if(mTrackingFocusRenderer != null && mTrackingFocusRenderer.isVisible()) {
                mTrackingFocusRenderer.setSurfaceDim(mSurfaceView.getLeft(), mSurfaceView.getTop(), mSurfaceView.getRight(), mSurfaceView.getBottom());
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.v(TAG, "surfaceDestroyed");
            mSurfaceHolder = null;
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

    private int mScreenRatio = CameraUtil.RATIO_UNKNOWN;
    private int mTopMargin = 0;
    private int mBottomMargin = 0;
    private ViewGroup mFilterLayout;

    private View mFilterModeSwitcher;
    private View mSceneModeSwitcher;
    private View mFrontBackSwitcher;
    private ImageView mMakeupButton;
    private SeekBar mMakeupSeekBar;
    private TextView mRecordingTimeView;
    private View mTimeLapseLabel;
    private RotateLayout mRecordingTimeRect;
    private PauseButton mPauseButton;
    private RotateImageView mMuteButton;

    int mPreviewWidth;
    int mPreviewHeight;
    private boolean mIsVideoUI = false;

    private void previewUIReady() {
        if((mSurfaceHolder != null && mSurfaceHolder.getSurface().isValid())) {
            mModule.onPreviewUIReady();
            mActivity.updateThumbnail(mThumbnail);
        }
    }

    private void previewUIDestroyed() {
        mModule.onPreviewUIDestroyed();
    }

    public TrackingFocusRenderer getTrackingFocusRenderer() {
        return mTrackingFocusRenderer;
    }

    public Point getDisplaySize() {
        return mDisplaySize;
    }

    public CaptureUI(CameraActivity activity, CaptureModule module, View parent) {
        mActivity = activity;
        mModule = module;
        mRootView = parent;
        mSettingsManager = SettingsManager.getInstance();
        mSettingsManager.registerListener(this);
        mActivity.getLayoutInflater().inflate(R.layout.capture_module,
                (ViewGroup) mRootView, true);
        mPreviewCover = mRootView.findViewById(R.id.preview_cover);
        // display the view
        mSurfaceView = (AutoFitSurfaceView) mRootView.findViewById(R.id.mdp_preview_content);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(callback);
        mSurfaceView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
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

        mSurfaceViewMono = (AutoFitSurfaceView) mRootView.findViewById(R.id.mdp_preview_content_mono);
        mSurfaceViewMono.setZOrderMediaOverlay(true);
        mSurfaceHolderMono = mSurfaceViewMono.getHolder();
        mSurfaceHolderMono.addCallback(callbackMono);

        mRenderOverlay = (RenderOverlay) mRootView.findViewById(R.id.render_overlay);
        mShutterButton = (ShutterButton) mRootView.findViewById(R.id.shutter_button);
        mVideoButton = (ImageView) mRootView.findViewById(R.id.video_button);
        mFilterModeSwitcher = mRootView.findViewById(R.id.filter_mode_switcher);
        mSceneModeSwitcher = mRootView.findViewById(R.id.scene_mode_switcher);
        mFrontBackSwitcher = mRootView.findViewById(R.id.front_back_switcher);
        mMakeupButton = (ImageView) mRootView.findViewById(R.id.ts_makeup_switcher);
        setMakeupButtonIcon();
        mMakeupSeekBar = (SeekBar)mRootView.findViewById(R.id.ts_makeup_seekbar);
        mMakeupSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
                int value = progresValue/10*10;
                if(mIsVideoUI && value == 0) {
                    mMakeupSeekBar.setProgress(10);
                    return;
                }
                mSettingsManager.setValue(SettingsManager.KEY_MAKEUP, value+"");
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        mMakeupButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                showOrHideMakeupSeekBar();
            }
        });
        mFlashButton = (FlashToggleButton) mRootView.findViewById(R.id.flash_button);
        initFilterModeButton();
        initSceneModeButton();
        initSwitchCamera();
        initFlashButton();

        mRecordingTimeView = (TextView) mRootView.findViewById(R.id.recording_time);
        mRecordingTimeRect = (RotateLayout) mRootView.findViewById(R.id.recording_time_rect);
        mTimeLapseLabel = mRootView.findViewById(R.id.time_lapse_label);
        mPauseButton = (PauseButton) mRootView.findViewById(R.id.video_pause);
        mPauseButton.setOnPauseButtonListener(this);

        mMuteButton = (RotateImageView)mRootView.findViewById(R.id.mute_button);
        mMuteButton.setVisibility(View.VISIBLE);
        if(!mModule.isAudioMute()) {
            mMuteButton.setImageResource(R.drawable.ic_unmuted_button);
        } else {
            mMuteButton.setImageResource(R.drawable.ic_muted_button);
        }
        mMuteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isEnabled = !mModule.isAudioMute();
                mModule.setMute(isEnabled, true);
                if (!isEnabled)
                    mMuteButton.setImageResource(R.drawable.ic_unmuted_button);
                else
                    mMuteButton.setImageResource(R.drawable.ic_muted_button);
            }
        });

        RotateImageView muteButton = (RotateImageView) mRootView.findViewById(R.id.mute_button);
        muteButton.setVisibility(View.GONE);

        mCameraControls = (OneUICameraControls) mRootView.findViewById(R.id.camera_controls);
        mFaceView = (Camera2FaceView) mRootView.findViewById(R.id.face_view);

        mActivity.getWindowManager().getDefaultDisplay().getSize(mDisplaySize);
        mScreenRatio = CameraUtil.determineRatio(mDisplaySize.x, mDisplaySize.y);
        if (mScreenRatio == CameraUtil.RATIO_16_9) {
            int l = mDisplaySize.x > mDisplaySize.y ? mDisplaySize.x : mDisplaySize.y;
            int tm = mActivity.getResources().getDimensionPixelSize(R.dimen.preview_top_margin);
            int bm = mActivity.getResources().getDimensionPixelSize(R.dimen.preview_bottom_margin);
            mTopMargin = l / 4 * tm / (tm + bm);
            mBottomMargin = l / 4 - mTopMargin;
        }

        if (mPieRenderer == null) {
            mPieRenderer = new PieRenderer(mActivity);
            mRenderOverlay.addRenderer(mPieRenderer);
        }

        if (mZoomRenderer == null) {
            mZoomRenderer = new ZoomRenderer(mActivity);
            mRenderOverlay.addRenderer(mZoomRenderer);
        }

        if(mTrackingFocusRenderer == null) {
            mTrackingFocusRenderer = new TrackingFocusRenderer(mActivity, mModule, this);
            mRenderOverlay.addRenderer(mTrackingFocusRenderer);
        }
        String trackingFocus = mSettingsManager.getValue(SettingsManager.KEY_TRACKINGFOCUS);
        if(trackingFocus != null && trackingFocus.equalsIgnoreCase("on")) {
            mTrackingFocusRenderer.setVisible(true);
        } else {
            mTrackingFocusRenderer.setVisible(false);
        }

        if (mGestures == null) {
            // this will handle gesture disambiguation and dispatching
            mGestures = new PreviewGestures(mActivity, this, mZoomRenderer, mPieRenderer, mTrackingFocusRenderer);
            mRenderOverlay.setGestures(mGestures);
        }

        mGestures.setRenderOverlay(mRenderOverlay);
        mRenderOverlay.requestLayout();

        mActivity.setPreviewGestures(mGestures);
        ((ViewGroup)mRootView).removeView(mRecordingTimeRect);
    }

    private void showOrHideMakeupSeekBar() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_MAKEUP);
        if(mMakeupSeekBar.getVisibility() == View.VISIBLE) {
            if(value != null && value.equals("0")) {
                if(mIsVideoUI) {
                    return;
                }
                mSettingsManager.setValue(SettingsManager.KEY_FACE_DETECTION, "off");
                mModule.restartSession(true);
            }
            mMakeupSeekBar.setVisibility(View.GONE);
        } else {
            mMakeupSeekBar.setVisibility(View.VISIBLE);
            if(value != null && value.equals("0")) {
                mSettingsManager.setValue(SettingsManager.KEY_MAKEUP, "40");
                mMakeupSeekBar.setProgress(40);
                mSettingsManager.setValue(SettingsManager.KEY_FACE_DETECTION, "on");
                mModule.restartSession(true);
            } else {
                try {
                    mMakeupSeekBar.setProgress(Integer.parseInt(mSettingsManager.getValue(SettingsManager.KEY_MAKEUP)));
                } catch(Exception e) {
                }
            }
        }
        setMakeupButtonIcon();
    }

    private void setMakeupButtonIcon() {
        final String value = mSettingsManager.getValue(SettingsManager.KEY_MAKEUP);
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                if(value != null && !value.equals("0")) {
                    mMakeupButton.setImageResource(R.drawable.beautify_on);
                } else {
                    mMakeupButton.setImageResource(R.drawable.beautify);
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
        String trackingFocus = mSettingsManager.getValue(SettingsManager.KEY_TRACKINGFOCUS);
        if(trackingFocus != null && trackingFocus.equalsIgnoreCase("on")) {
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

    // called from onResume but only the first time
    public void initializeFirstTime() {
        // Initialize shutter button.
        mShutterButton.setOnShutterButtonListener(mModule);
        mShutterButton.setVisibility(View.VISIBLE);
        mVideoButton.setVisibility(View.VISIBLE);
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
                ((ViewGroup) mRootView).removeView(mFilterLayout);
                mFilterLayout = null;
            }
        }
    }

    public void openSettingsMenu() {
        clearFocus();
        removeFilterMenu(false);
        Intent intent = new Intent(mActivity, SettingsActivity.class);
        mActivity.startActivity(intent);
    }

    public void initSwitchCamera() {
        mFrontBackSwitcher.setVisibility(View.INVISIBLE);
        String value = mSettingsManager.getValue(SettingsManager.KEY_CAMERA_ID);
        if (value == null)
            return;

        mFrontBackSwitcher.setVisibility(View.VISIBLE);
        mFrontBackSwitcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
                Intent intent = new Intent(mActivity, SceneModeActivity.class);
                mActivity.startActivity(intent);
            }
        });
    }

    public void initFilterModeButton() {
        mFilterModeSwitcher.setVisibility(View.INVISIBLE);
        String value = mSettingsManager.getValue(SettingsManager.KEY_COLOR_EFFECT);
        if (value == null) return;

        mFilterModeSwitcher.setVisibility(View.VISIBLE);
        mFilterModeSwitcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addFilterMode();
            }
        });
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
            mVideoButton.setImageResource(R.drawable.video_stop);
            mRecordingTimeView.setText("");
            ((ViewGroup)mRootView).addView(mRecordingTimeRect);
            mMuteButton.setVisibility(View.VISIBLE);
        } else {
            mFlashButton.setVisibility(View.VISIBLE);
            mFlashButton.init(false);
            mVideoButton.setImageResource(R.drawable.video_capture);
            ((ViewGroup)mRootView).removeView(mRecordingTimeRect);
            mMuteButton.setVisibility(View.INVISIBLE);
        }
    }

    public void resetTrackingFocus() {
        String trackingFocus = mSettingsManager.getValue(SettingsManager.KEY_TRACKINGFOCUS);
        if(trackingFocus != null && trackingFocus.equalsIgnoreCase("on")) {
            mTrackingFocusRenderer.setVisible(false);
            mTrackingFocusRenderer.setVisible(true);
        }
    }

    public void hideUIwhileRecording() {
        mCameraControls.setWillNotDraw(true);
        mFrontBackSwitcher.setVisibility(View.INVISIBLE);
        mFilterModeSwitcher.setVisibility(View.INVISIBLE);
        mSceneModeSwitcher.setVisibility(View.INVISIBLE);

        String value = mSettingsManager.getValue(SettingsManager.KEY_MAKEUP);
        if(value != null && value.equals("0")) {
            mMakeupButton.setVisibility(View.INVISIBLE);
        }
        mIsVideoUI = true;
    }

    public void showUIafterRecording() {
        mCameraControls.setWillNotDraw(false);
        mFrontBackSwitcher.setVisibility(View.VISIBLE);
        mFilterModeSwitcher.setVisibility(View.VISIBLE);
        mSceneModeSwitcher.setVisibility(View.VISIBLE);
        mMakeupButton.setVisibility(View.VISIBLE);
        mIsVideoUI = false;
    }

    public void addFilterMode() {
        if (mSettingsManager.getValue(SettingsManager.KEY_COLOR_EFFECT) == null)
            return;

        int rotation = CameraUtil.getDisplayRotation(mActivity);
        boolean mIsDefaultToPortrait = CameraUtil.isDefaultToPortrait(mActivity);
        if (!mIsDefaultToPortrait) {
            rotation = (rotation + 90) % 360;
        }
        WindowManager wm = (WindowManager) mActivity.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        CharSequence[] entries = mSettingsManager.getEntries(SettingsManager.KEY_COLOR_EFFECT);

        Resources r = mActivity.getResources();
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
        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        FrameLayout gridOuterLayout = (FrameLayout) inflater.inflate(
                gridRes, null, false);
        gridOuterLayout.setBackgroundColor(android.R.color.transparent);
        removeFilterMenu(false);
        mFilterMenuStatus = FILTER_MENU_ON;
        mFilterLayout = new LinearLayout(mActivity);

        ViewGroup.LayoutParams params = null;
        if (!portrait) {
            params = new ViewGroup.LayoutParams(size, FrameLayout.LayoutParams.MATCH_PARENT);
            mFilterLayout.setLayoutParams(params);
            ((ViewGroup) mRootView).addView(mFilterLayout);
        } else {
            params = new ViewGroup.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, size);
            mFilterLayout.setLayoutParams(params);
            ((ViewGroup) mRootView).addView(mFilterLayout);
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
        if (View.LAYOUT_DIRECTION_RTL == TextUtils
                .getLayoutDirectionFromLocale(Locale.getDefault())) {
            vp.translationXBy(v.getWidth()).setDuration(ANIMATION_DURATION);
        } else {
            vp.translationXBy(-v.getWidth()).setDuration(ANIMATION_DURATION);
        }
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
        if (View.LAYOUT_DIRECTION_RTL == TextUtils
                .getLayoutDirectionFromLocale(Locale.getDefault())) {
            switch (orientation) {
                case 0:
                    dest = v.getX();
                    v.setX(-(dest - delta));
                    vp.translationX(dest);
                    break;
                case 90:
                    dest = v.getY();
                    v.setY(-(dest + delta));
                    vp.translationY(dest);
                    break;
                case 180:
                    dest = v.getX();
                    v.setX(-(dest + delta));
                    vp.translationX(dest);
                    break;
                case 270:
                    dest = v.getY();
                    v.setY(-(dest - delta));
                    vp.translationY(dest);
                    break;
            }
        } else {
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
        if (mFlashButton != null) mFlashButton.setVisibility(status);
        if (mFrontBackSwitcher != null) mFrontBackSwitcher.setVisibility(status);
        if (mSceneModeSwitcher != null) mSceneModeSwitcher.setVisibility(status);
        if (mFilterModeSwitcher != null) mFilterModeSwitcher.setVisibility(status);
        if (mFilterModeSwitcher != null) mFilterModeSwitcher.setVisibility(status);
        if (mMakeupButton != null) mMakeupButton.setVisibility(status);
    }

    public void initializeControlByIntent() {
        mThumbnail = (ImageView) mRootView.findViewById(R.id.preview_thumb);
        mThumbnail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!CameraControls.isAnimating() && !mModule.isTakingPicture() &&
                        !mModule.isRecordingVideo())
                    mActivity.gotoGallery();
            }
        });
    }

    public void doShutterAnimation() {
    }

    public void showUI() {
        if (!mUIhidden)
            return;
        mUIhidden = false;
        mPieRenderer.setBlockFocus(false);
        mCameraControls.showUI();
    }

    public void hideUI() {
        if (mUIhidden)
            return;
        mUIhidden = true;
        mPieRenderer.setBlockFocus(true);
        mCameraControls.hideUI();
    }

    public void cleanUpMenus() {
        showUI();
        mActivity.setSystemBarsVisibility(false);
    }

    public boolean arePreviewControlsVisible() {
        return !mUIhidden;
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

    public SurfaceHolder getSurfaceHolder() {
        return mSurfaceHolder;
    }

    private class MonoDummyListener implements Allocation.OnBufferAvailableListener {
        ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic;
        public MonoDummyListener(ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic) {
            this.yuvToRgbIntrinsic = yuvToRgbIntrinsic;
        }

        @Override
        public void onBufferAvailable(Allocation a) {
            if(mMonoDummyAllocation != null) {
                mMonoDummyAllocation.ioReceive();
                mIsMonoDummyAllocationEverUsed = true;
                if(mSurfaceViewMono.getVisibility() == View.VISIBLE) {
                    try {
                        yuvToRgbIntrinsic.forEach(mMonoDummyOutputAllocation);
                        mMonoDummyOutputAllocation.ioSend();
                    } catch(Exception e)
                    {
                        Log.e(TAG, e.toString());
                    }
                }
            }
        }
    }

    public Surface getMonoDummySurface() {
        if (mMonoDummyAllocation == null) {
            RenderScript rs = RenderScript.create(mActivity);
            Type.Builder yuvTypeBuilder = new Type.Builder(rs, Element.YUV(rs));
            yuvTypeBuilder.setX(mPreviewWidth);
            yuvTypeBuilder.setY(mPreviewHeight);
            yuvTypeBuilder.setYuvFormat(ImageFormat.YUV_420_888);
            mMonoDummyAllocation = Allocation.createTyped(rs, yuvTypeBuilder.create(), Allocation.USAGE_IO_INPUT|Allocation.USAGE_SCRIPT);
            ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.RGBA_8888(rs));
            yuvToRgbIntrinsic.setInput(mMonoDummyAllocation);

            if(mSettingsManager.getValue(SettingsManager.KEY_MONO_PREVIEW).equalsIgnoreCase("on")) {
                Type.Builder rgbTypeBuilder = new Type.Builder(rs, Element.RGBA_8888(rs));
                rgbTypeBuilder.setX(mPreviewWidth);
                rgbTypeBuilder.setY(mPreviewHeight);
                mMonoDummyOutputAllocation = Allocation.createTyped(rs, rgbTypeBuilder.create(), Allocation.USAGE_SCRIPT | Allocation.USAGE_IO_OUTPUT);
                mMonoDummyOutputAllocation.setSurface(mSurfaceHolderMono.getSurface());
                mActivity.runOnUiThread(new Runnable() {
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
        mActivity.getLayoutInflater().inflate(R.layout.count_down_to_capture,
                (ViewGroup) mRootView, true);
        mCountDownView = (CountDownView) (mRootView.findViewById(R.id.count_down_to_capture));
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

    public void startCountDown(int sec, boolean playSound) {
        if (mCountDownView == null) initializeCountDown();
        mCountDownView.startCountDown(sec, playSound);
        hideUIWhileCountDown();
    }

    public void onPause() {
        cancelCountDown();
        collapseCameraControls();

        if (mFaceView != null) mFaceView.clear();
        if(mTrackingFocusRenderer != null) {
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
        String trackingFocus = mSettingsManager.getValue(SettingsManager.KEY_TRACKINGFOCUS);
        if (trackingFocus != null && trackingFocus.equalsIgnoreCase("on")) {
            if (mPieRenderer != null) {
                mPieRenderer.clear();
            }
            return mTrackingFocusRenderer;
        }

        return (mFaceView != null && mFaceView.faceExists()) ? mFaceView : mPieRenderer;
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
    }

    @Override
    public void setFocusPosition(int x, int y) {
        mPieRenderer.setFocus(x, y);
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

    public void onStartFaceDetection(int orientation, boolean mirror, Rect cameraBound) {
        mFaceView.setBlockDraw(false);
        mFaceView.clear();
        mFaceView.setVisibility(View.VISIBLE);
        mFaceView.setDisplayOrientation(orientation);
        mFaceView.setMirror(mirror);
        mFaceView.setCameraBound(cameraBound);
        mFaceView.resume();
    }

    public void updateFaceViewCameraBound(Rect cameraBound) {
        mFaceView.setCameraBound(cameraBound);
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

    public void onFaceDetection(android.hardware.camera2.params.Face[] faces) {
        mFaceView.setFaces(faces);
    }

    public Point getSurfaceViewSize() {
        Point point = new Point();
        if (mSurfaceView != null) point.set(mSurfaceView.getWidth(), mSurfaceView.getHeight());
        return point;
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
            if (orientation == 180) {
                mRecordingTimeRect.setOrientation(0, false);
                mRecordingTimeView.setRotation(180);
            } else {
                mRecordingTimeView.setRotation(0);
                mRecordingTimeRect.setOrientation(orientation, false);
            }
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
        mSurfaceView.getLocationInWindow(surfaceViewLocation);
        int surfaceViewX = surfaceViewLocation[0];
        int surfaceViewY = surfaceViewLocation[1];
        xy[0] = x - surfaceViewX;
        xy[1] = y - surfaceViewY;
        return (x > surfaceViewX) && (x < surfaceViewX + mSurfaceView.getWidth())
                && (y > surfaceViewY) && (y < surfaceViewY + mSurfaceView.getHeight());
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
    }

    public void startSelfieFlash() {
        if (mSelfieView == null)
            mSelfieView = (SelfieFlashView) (mRootView.findViewById(R.id.selfie_flash));
        mSelfieView.bringToFront();
        mSelfieView.open();
        mScreenBrightness = setScreenBrightness(1F);
    }

    public void stopSelfieFlash() {
        if (mSelfieView == null)
            mSelfieView = (SelfieFlashView) (mRootView.findViewById(R.id.selfie_flash));
        mSelfieView.close();
        if (mScreenBrightness != 0.0f)
            setScreenBrightness(mScreenBrightness);
    }

    private float setScreenBrightness(float brightness) {
        float originalBrightness;
        Window window = mActivity.getWindow();
        WindowManager.LayoutParams layout = window.getAttributes();
        originalBrightness = layout.screenBrightness;
        layout.screenBrightness = brightness;
        window.setAttributes(layout);
        return originalBrightness;
    }

    public void hideSurfaceView() {
        mSurfaceView.setVisibility(View.INVISIBLE);
    }

    public void showSurfaceView() {
        mSurfaceView.getHolder().setFixedSize(mPreviewWidth, mPreviewHeight);
        mSurfaceView.setAspectRatio(mPreviewHeight, mPreviewWidth);
        mSurfaceView.setVisibility(View.VISIBLE);
        mIsVideoUI = false;
    }

    public boolean setPreviewSize(int width, int height) {
        Log.d(TAG, "setPreviewSize " + width + " " + height);
        boolean changed = (width != mPreviewWidth) || (height != mPreviewHeight);
        mPreviewWidth = width;
        mPreviewHeight = height;
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

}
