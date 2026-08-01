package com.smartbus.driver.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.snackbar.Snackbar;
import com.smartbus.driver.databinding.ActivityLoginBinding;
import com.smartbus.driver.viewmodel.LoginViewModel;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private LoginViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        if (viewModel.isRememberUsername()) {
            binding.rememberCheckBox.setChecked(true);
            binding.usernameEditText.setText(viewModel.getSavedUsername());
        }

        binding.loginButton.setOnClickListener(v -> viewModel.login(
                binding.usernameEditText.getText() != null
                        ? binding.usernameEditText.getText().toString()
                        : "",
                binding.passwordEditText.getText() != null
                        ? binding.passwordEditText.getText().toString()
                        : "",
                binding.rememberCheckBox.isChecked()
        ));

        viewModel.getLoginState().observe(this, state -> {
            if (state == null) {
                return;
            }
            boolean loading = state.isLoading();
            binding.loginProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
            binding.loginButton.setEnabled(!loading);

            if (state.isSuccess()) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
            } else if (state.isError()) {
                Snackbar.make(
                        binding.snackbarAnchor,
                        state.getMessage() != null ? state.getMessage() : getString(com.smartbus.driver.R.string.error_default),
                        Snackbar.LENGTH_LONG
                ).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
