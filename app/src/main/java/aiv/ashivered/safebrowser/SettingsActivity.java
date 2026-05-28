package aiv.ashivered.safebrowser;

import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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

        private int getPaddingPx() {
            return (int) (16 * getResources().getDisplayMetrics().density + 0.5f);
        }

        private EditText buildPasswordInput() {
            EditText input = new EditText(requireContext());
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            input.setHint(R.string.password_hint);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            int p = getPaddingPx();
            lp.setMargins(p, p, p, p);
            input.setLayoutParams(lp);
            return input;
        }

        private LinearLayout wrapInContainer(EditText input) {
            LinearLayout container = new LinearLayout(requireContext());
            container.setOrientation(LinearLayout.VERTICAL);
            container.addView(input);
            return container;
        }

        private void showSetPasswordDialog() {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            builder.setTitle(R.string.set_password_title);
            EditText input = buildPasswordInput();
            builder.setView(wrapInContainer(input));
            builder.setPositiveButton(R.string.set, null);
            builder.setNegativeButton(R.string.cancel, (d, w) -> {
                lockSettingsPref.setChecked(false);
                PasswordUtils.setSettingsLockEnabled(requireContext(), false);
                d.cancel();
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

        private void showConfirmPasswordDialog(String firstPassword) {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            builder.setTitle(R.string.confirm_password_title);
            EditText input = buildPasswordInput();
            builder.setView(wrapInContainer(input));
            builder.setPositiveButton(R.string.confirm, null);
            builder.setNegativeButton(R.string.cancel, (d, w) -> {
                lockSettingsPref.setChecked(false);
                PasswordUtils.setSettingsLockEnabled(requireContext(), false);
                d.cancel();
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
                            lockSettingsPref.setChecked(true);
                            Toast.makeText(requireContext(), R.string.password_set_success, Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        } else {
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

        private void showEnterPasswordDialog(boolean forDisabling) {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            builder.setTitle(R.string.enter_password_title);
            EditText input = buildPasswordInput();
            builder.setView(wrapInContainer(input));
            builder.setPositiveButton(forDisabling ? R.string.disable : R.string.enter, null);
            builder.setNegativeButton(R.string.cancel, (d, w) -> {
                if (forDisabling) {
                    lockSettingsPref.setChecked(true);
                    PasswordUtils.setSettingsLockEnabled(requireContext(), true);
                }
                d.cancel();
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
