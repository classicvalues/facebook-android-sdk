/*
 * Copyright (c) 2014-present, Facebook, Inc. All rights reserved.
 *
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Facebook.
 *
 * As with any software that integrates with the Facebook platform, your use of
 * this software is subject to the Facebook Developer Principles and Policies
 * [http://developers.facebook.com/policy/]. This copyright notice shall be
 * included in all copies or substantial portions of the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.facebook.login;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.facebook.common.R;
import com.facebook.internal.qualityvalidation.Excuse;
import com.facebook.internal.qualityvalidation.ExcusesForDesignViolations;

/**
 * This Fragment is a necessary part of the overall Facebook login process but is not meant to be
 * used directly.
 *
 * @see com.facebook.FacebookActivity
 */
@ExcusesForDesignViolations(@Excuse(type = "MISSING_UNIT_TEST", reason = "Legacy"))
public class LoginFragment extends Fragment {
  static final String RESULT_KEY = "com.facebook.LoginFragment:Result";
  static final String REQUEST_KEY = "com.facebook.LoginFragment:Request";
  static final String EXTRA_REQUEST = "request";

  private static final String TAG = "LoginFragment";
  private static final String NULL_CALLING_PKG_ERROR_MSG =
      "Cannot call LoginFragment with a null calling package. "
          + "This can occur if the launchMode of the caller is singleInstance.";
  private static final String SAVED_LOGIN_CLIENT = "loginClient";

  private String callingPackage;
  private LoginClient loginClient;
  private LoginClient.Request request;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (savedInstanceState != null) {
      loginClient = savedInstanceState.getParcelable(SAVED_LOGIN_CLIENT);
      loginClient.setFragment(this);
    } else {
      loginClient = createLoginClient();
    }

    loginClient.setOnCompletedListener(
        new LoginClient.OnCompletedListener() {
          @Override
          public void onCompleted(LoginClient.Result outcome) {
            onLoginClientCompleted(outcome);
          }
        });

    Activity activity = getActivity();
    if (activity == null) {
      return;
    }

    initializeCallingPackage(activity);
    Intent intent = activity.getIntent();
    if (intent != null) {
      Bundle bundle = intent.getBundleExtra(REQUEST_KEY);
      if (bundle != null) {
        request = bundle.getParcelable(EXTRA_REQUEST);
      }
    }
  }

  protected LoginClient createLoginClient() {
    return new LoginClient(this);
  }

  @Override
  public void onDestroy() {
    loginClient.cancelCurrentHandler();
    super.onDestroy();
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    final View view = inflater.inflate(getLayoutResId(), container, false);
    final View progressBar = view.findViewById(R.id.com_facebook_login_fragment_progress_bar);

    loginClient.setBackgroundProcessingListener(
        new LoginClient.BackgroundProcessingListener() {
          @Override
          public void onBackgroundProcessingStarted() {
            progressBar.setVisibility(View.VISIBLE);
          }

          @Override
          public void onBackgroundProcessingStopped() {
            progressBar.setVisibility(View.GONE);
          }
        });

    return view;
  }

  @LayoutRes
  protected int getLayoutResId() {
    return R.layout.com_facebook_login_fragment;
  }

  private void onLoginClientCompleted(LoginClient.Result outcome) {
    request = null;

    int resultCode =
        (outcome.code == LoginClient.Result.Code.CANCEL)
            ? Activity.RESULT_CANCELED
            : Activity.RESULT_OK;

    Bundle bundle = new Bundle();
    bundle.putParcelable(RESULT_KEY, outcome);

    Intent resultIntent = new Intent();
    resultIntent.putExtras(bundle);

    // The activity might be detached we will send a cancel result in onDetach
    if (isAdded()) {
      getActivity().setResult(resultCode, resultIntent);
      getActivity().finish();
    }
  }

  @Override
  public void onResume() {
    super.onResume();

    // If the calling package is null, this generally means that the callee was started
    // with a launchMode of singleInstance. Unfortunately, Android does not allow a result
    // to be set when the callee is a singleInstance, so we log an error and return.
    if (callingPackage == null) {
      Log.e(TAG, NULL_CALLING_PKG_ERROR_MSG);
      getActivity().finish();
      return;
    }

    loginClient.startOrContinueAuth(request);
  }

  @Override
  public void onPause() {
    super.onPause();

    final View progressBar =
        getView() == null
            ? null
            : getView().findViewById(R.id.com_facebook_login_fragment_progress_bar);
    if (progressBar != null) {
      progressBar.setVisibility(View.GONE);
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    loginClient.onActivityResult(requestCode, resultCode, data);
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);

    outState.putParcelable(SAVED_LOGIN_CLIENT, loginClient);
  }

  private void initializeCallingPackage(final Activity activity) {
    ComponentName componentName = activity.getCallingActivity();
    if (componentName == null) {
      return;
    }
    callingPackage = componentName.getPackageName();
  }

  LoginClient getLoginClient() {
    return loginClient;
  }
}
