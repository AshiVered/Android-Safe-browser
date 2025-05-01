package aiv.ashivered.safebrowser;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
             actionBar.setHomeAsUpIndicator(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
             actionBar.setDisplayShowHomeEnabled(true);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }


    public static class SettingsFragment extends PreferenceFragmentCompat {

        private SwitchPreferenceCompat lockSettingsPref;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            lockSettingsPref = findPreference("lock_settings");

            if (lockSettingsPref != null) {
                lockSettingsPref.setChecked(PasswordUtils.isSettingsLockEnabled(requireContext()));

                lockSettingsPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean isEnabled = (Boolean) newValue;
                    if (isEnabled) {
                        if (!PasswordUtils.hasPasswordSet(requireContext())) {
                            showSetPasswordDialog();

                            return false;
                        } else {
                            PasswordUtils.setSettingsLockEnabled(requireContext(), true);
                            return true;
                        }
                    } else {
                        if (PasswordUtils.hasPasswordSet(requireContext())) {
                            showEnterPasswordDialog(true);
                            return false;
                        } else {
                            PasswordUtils.setSettingsLockEnabled(requireContext(), false);
                            return true;
                        }
                    }
                });
            }
        }

        private void showSetPasswordDialog() {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            builder.setTitle(R.string.set_password_title);

            final EditText input = new EditText(requireContext());
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            input.setHint(R.string.password_hint);
            LinearLayout container = new LinearLayout(requireContext());
            container.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            int padding_in_dp = 16; // 16 dp
            final float scale = getResources().getDisplayMetrics().density;
            int padding_in_px = (int) (padding_in_dp * scale + 0.5f);
            lp.setMargins(padding_in_px, padding_in_px, padding_in_px, padding_in_px);
            input.setLayoutParams(lp);
            container.addView(input);
            builder.setView(container);


            builder.setPositiveButton(R.string.set, null);
            builder.setNegativeButton(R.string.cancel, (dialog, which) -> {
                lockSettingsPref.setChecked(false);
                PasswordUtils.setSettingsLockEnabled(requireContext(), false);
                dialog.cancel();
            });

            AlertDialog dialog = builder.create();

            dialog.setOnShowListener(d -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String password = input.getText().toString();
                    if (PasswordUtils.isValidPasswordFormat(password)) {
                        dialog.dismiss();
                        showConfirmPasswordDialog(password);
                    } else {
                        input.setError(getString(R.string.password_invalid_format));
                    }
                });
            });

            dialog.show();
        }


        private void showConfirmPasswordDialog(final String firstPassword) {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            builder.setTitle(R.string.confirm_password_title);

            final EditText input = new EditText(requireContext());
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            input.setHint(R.string.password_hint);
            // Add padding
            LinearLayout container = new LinearLayout(requireContext());
            container.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            int padding_in_dp = 16;
            final float scale = getResources().getDisplayMetrics().density;
            int padding_in_px = (int) (padding_in_dp * scale + 0.5f);
            lp.setMargins(padding_in_px, padding_in_px, padding_in_px, padding_in_px);
            input.setLayoutParams(lp);
            container.addView(input);
            builder.setView(container);


            builder.setPositiveButton(R.string.confirm, null);
            builder.setNegativeButton(R.string.cancel, (dialog, which) -> {
                lockSettingsPref.setChecked(false);
                PasswordUtils.setSettingsLockEnabled(requireContext(), false);
                dialog.cancel();
            });

             AlertDialog dialog = builder.create();

             dialog.setOnShowListener(d -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String confirmPassword = input.getText().toString();
                     if (firstPassword.equals(confirmPassword)) {
                        String hash = PasswordUtils.hashPassword(firstPassword);
                        if (hash != null) {
                            PasswordUtils.setPasswordHash(requireContext(), hash);
                            PasswordUtils.setSettingsLockEnabled(requireContext(), true);
                            lockSettingsPref.setChecked(true); // Update switch state
                            Toast.makeText(requireContext(), R.string.password_set_success, Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        } else {
                             Toast.makeText(requireContext(), "Error hashing password", Toast.LENGTH_SHORT).show();
                             lockSettingsPref.setChecked(false);
                             PasswordUtils.setSettingsLockEnabled(requireContext(), false);
                             dialog.dismiss();
                        }
                    } else {
                        input.setError(getString(R.string.password_mismatch));
                    }
                });
            });
            dialog.show();
        }


        private void showEnterPasswordDialog(final boolean forDisabling) {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            builder.setTitle(R.string.enter_password_title);

            final EditText input = new EditText(requireContext());
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            input.setHint(R.string.password_hint);
            // Add padding
            LinearLayout container = new LinearLayout(requireContext());
            container.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            int padding_in_dp = 16;
            final float scale = getResources().getDisplayMetrics().density;
            int padding_in_px = (int) (padding_in_dp * scale + 0.5f);
            lp.setMargins(padding_in_px, padding_in_px, padding_in_px, padding_in_px);
            input.setLayoutParams(lp);
            container.addView(input);
            builder.setView(container);


            builder.setPositiveButton(forDisabling ? R.string.disable : R.string.enter, null); // Set null listener
            builder.setNegativeButton(R.string.cancel, (dialog, which) -> {
                 if (forDisabling) {
                     lockSettingsPref.setChecked(true);
                     PasswordUtils.setSettingsLockEnabled(requireContext(), true);
                 }
                 dialog.cancel();
            });

            AlertDialog dialog = builder.create();

            dialog.setOnShowListener(d -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String password = input.getText().toString();
                    if (PasswordUtils.checkPassword(requireContext(), password)) {
                        if (forDisabling) {
                            PasswordUtils.clearPassword(requireContext());
                            PasswordUtils.setSettingsLockEnabled(requireContext(), false);
                            lockSettingsPref.setChecked(false);
                            Toast.makeText(requireContext(), R.string.password_cleared_success, Toast.LENGTH_SHORT).show();
                        }

                        dialog.dismiss();

                    } else {
                        // Incorrect password
                         input.setError(getString(R.string.incorrect_password));
                         if (forDisabling) {
                             lockSettingsPref.setChecked(true);
                             PasswordUtils.setSettingsLockEnabled(requireContext(), true);
                         }
                    }
                });
            });

            dialog.show();
        }
    }
}