package com.github.openwebnet.view.profile;

import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import com.github.openwebnet.R;
import com.github.openwebnet.component.Injector;
import com.github.openwebnet.model.firestore.UserProfileModel;
import com.github.openwebnet.service.FirebaseService;
import com.github.openwebnet.service.UtilityService;
import com.github.openwebnet.view.MainActivity;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import butterknife.BindString;
import butterknife.BindView;
import butterknife.ButterKnife;
import rx.functions.Action0;

/*
 * - add swipe to refresh
 * - menu item for each card
 * - FAB create/reset/logout
 * >>> https://github.com/leinardi/FloatingActionButtonSpeedDial
 * https://github.com/Clans/FloatingActionButton
 * https://github.com/futuresimple/android-floating-action-button
 * https://github.com/makovkastar/FloatingActionButton
 * https://github.com/wangjiegulu/RapidFloatingActionButton
 */
public class ProfileActivity extends AppCompatActivity {

    private static final Logger log = LoggerFactory.getLogger(ProfileActivity.class);

    @BindView(R.id.recyclerViewProfile)
    RecyclerView mRecyclerView;

    @BindString(R.string.validation_required)
    String labelValidationRequired;

    @Inject
    FirebaseService firebaseService;

    @Inject
    UtilityService utilityService;

    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private List<UserProfileModel> profileItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        Injector.getApplicationComponent().inject(this);
        ButterKnife.bind(this);

        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);

        mAdapter = new ProfileAdapter(this, profileItems);
        mRecyclerView.setAdapter(mAdapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshProfiles();
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_profile_create:
                showCreateDialog();
                return true;
            case R.id.action_profile_reset:
                showConfirmationDialog(
                    R.string.dialog_profile_reset_title,
                    R.string.dialog_profile_reset_message,
                    this::resetProfile);
                return true;
            case R.id.action_profile_logout:
                showConfirmationDialog(
                    R.string.dialog_profile_logout_title,
                    R.string.dialog_profile_logout_message,
                    this::logoutProfile);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_profile, menu);
        return true;
    }

    private void showConfirmationDialog(int titleStringId, int messageStringId, Action0 actionOk) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle(titleStringId)
            .setMessage(messageStringId)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(android.R.string.cancel, null);

        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v -> {
                actionOk.call();
                dialog.dismiss();
            });
    }

    // TODO check client side max limit of 10
    private void showCreateDialog() {
        View layout = LayoutInflater.from(this).inflate(R.layout.dialog_profile_create, null);
        EditText editTextName = layout.findViewById(R.id.editTextDialogProfileCreateName);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setView(layout)
            .setTitle(R.string.dialog_profile_create_title)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(android.R.string.cancel, null);

        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v -> {
                if (utilityService.isBlankText(editTextName)) {
                    editTextName.setError(labelValidationRequired);
                } else {
                    createProfile(utilityService.sanitizedText(editTextName));
                    dialog.dismiss();
                }
            });
    }

    // TODO check internet connection
    // TODO toggle loader
    private void refreshProfiles() {
        firebaseService.getUserProfiles()
            // TODO message
            .doOnError(error -> showError(error, "refreshProfiles failed"))
            .subscribe(results -> {
                log.info("refreshProfiles: size={}", results.size());
                profileItems.clear();
                profileItems.addAll(results);
                mAdapter.notifyDataSetChanged();
            });
    }

    private void createProfile(String name) {
        firebaseService.updateUser()
            .flatMap(aVoid -> firebaseService.addProfile(name))
            // TODO message
            .doOnError(error -> showError(error, "createProfile failed"))
            .subscribe(profileId -> {
                log.info("createProfile succeeded: profileId={}", profileId);
                refreshProfiles();
                showSnackbar("TODO profile create success");
            });
    }

    private void resetProfile() {
        firebaseService.resetLocalProfile()
            // TODO message
            .doOnError(error -> showError(error, "resetProfile failed"))
            .subscribe(profileId -> {
                log.info("terminating ProfileActivity after reset");
                setResult(MainActivity.RESULT_CODE_PROFILE_RESET);
                finish();
            });
    }

    // TODO hide
    private void logoutProfile() {
        firebaseService.signOut(this, () -> {
            log.info("terminating ProfileActivity after logout");
            finish();
        });
    }

    private void showError(Throwable error, String message) {
        log.error("showError: {}", message, error);
        showSnackbar(message);
    }

    // TODO int stringId
    // TODO toast ???
    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show();
    }

    /**
     *
     */
    public static class OnRefreshProfilesEvent {

        public OnRefreshProfilesEvent() {}
    }

    // fired by ProfileAdapter
    @Subscribe
    public void onEvent(OnRefreshProfilesEvent event) {
        refreshProfiles();
    }

    /**
     *
     */
    public static class OnShowSnackbarEvent {

        private final String message;

        public OnShowSnackbarEvent(String message) {
            this.message = message;
        }
    }

    // fired by ProfileAdapter
    @Subscribe
    public void onEvent(OnShowSnackbarEvent event) {
        showSnackbar(event.message);
    }

    /**
     *
     */
    public static class OnShowConfirmationDialogEvent {


        private final int titleStringId;
        private final int messageStringId;
        private final Action0 actionOk;

        public OnShowConfirmationDialogEvent(int titleStringId, int messageStringId, Action0 actionOk) {
            this.titleStringId = titleStringId;
            this.messageStringId = messageStringId;
            this.actionOk = actionOk;
        }
    }

    // fired by ProfileAdapter
    @Subscribe
    public void onEvent(OnShowConfirmationDialogEvent event) {
        showConfirmationDialog(event.titleStringId, event.messageStringId, event.actionOk);
    }

}
