package com.smartbus.driver.utils;

import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.google.android.material.button.MaterialButton;
import com.smartbus.driver.R;

public final class StateOverlayHelper {

    public interface RetryAction {
        void onRetry();
    }

    private StateOverlayHelper() {
    }

    public static void showLoading(@Nullable View overlayRoot) {
        if (overlayRoot == null) {
            return;
        }
        overlayRoot.setVisibility(View.VISIBLE);
        setChildVisible(overlayRoot, R.id.loadingState, true);
        setChildVisible(overlayRoot, R.id.emptyState, false);
        setChildVisible(overlayRoot, R.id.errorState, false);
    }

    public static void showEmpty(@Nullable View overlayRoot, @Nullable String title, @Nullable String message) {
        if (overlayRoot == null) {
            return;
        }
        overlayRoot.setVisibility(View.VISIBLE);
        setChildVisible(overlayRoot, R.id.loadingState, false);
        setChildVisible(overlayRoot, R.id.emptyState, true);
        setChildVisible(overlayRoot, R.id.errorState, false);
        setText(overlayRoot, R.id.emptyTitle, title);
        setText(overlayRoot, R.id.emptyMessage, message);
    }

    public static void showError(
            @Nullable View overlayRoot,
            @Nullable String title,
            @Nullable String message,
            @Nullable RetryAction retryAction
    ) {
        if (overlayRoot == null) {
            return;
        }
        overlayRoot.setVisibility(View.VISIBLE);
        setChildVisible(overlayRoot, R.id.loadingState, false);
        setChildVisible(overlayRoot, R.id.emptyState, false);
        setChildVisible(overlayRoot, R.id.errorState, true);
        setText(overlayRoot, R.id.errorTitle, title);
        setText(overlayRoot, R.id.errorMessage, message);
        MaterialButton retryButton = overlayRoot.findViewById(R.id.retryButton);
        if (retryButton != null) {
            retryButton.setOnClickListener(v -> {
                if (retryAction != null) {
                    retryAction.onRetry();
                }
            });
        }
    }

    public static void hide(@Nullable View overlayRoot) {
        if (overlayRoot != null) {
            overlayRoot.setVisibility(View.GONE);
        }
    }

    private static void setChildVisible(View root, int id, boolean visible) {
        View child = root.findViewById(id);
        if (child != null) {
            child.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private static void setText(View root, int id, @Nullable String value) {
        TextView textView = root.findViewById(id);
        if (textView != null && value != null) {
            textView.setText(value);
        }
    }
}
