package com.aaplab.robird;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.NotificationCompat;

import com.aaplab.robird.data.entity.Account;
import com.aaplab.robird.data.entity.UserList;
import com.aaplab.robird.data.model.AccountModel;
import com.aaplab.robird.data.model.DirectsModel;
import com.aaplab.robird.data.model.PrefsModel;
import com.aaplab.robird.data.model.TimelineModel;
import com.aaplab.robird.data.model.UserListsModel;
import com.aaplab.robird.data.provider.contract.TweetContract;
import com.aaplab.robird.inject.Inject;
import com.aaplab.robird.ui.activity.HomeActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.TaskParams;

import java.util.List;

import timber.log.Timber;

/**
 * Created by majid on 02.09.15.
 */
public final class TimelineUpdateService extends GcmTaskService {

    public static PeriodicTask create(long periodInSeconds) {
        return new PeriodicTask.Builder()
                .setService(TimelineUpdateService.class)
                .setPeriod(periodInSeconds)
                .setTag("timeline_update")
                .setUpdateCurrent(true)
                .setPersisted(true)
                .build();
    }

    @Override
    public int onRunTask(TaskParams taskParams) {
        try {
            final PrefsModel prefsModel = new PrefsModel();

            for (final Account account : new AccountModel().accounts().toBlocking().first()) {
                new TimelineModel(account, TimelineModel.HOME_ID).update().toBlocking().first();
                new TimelineModel(account, TimelineModel.RETWEETS_ID).update().toBlocking().first();
                new TimelineModel(account, TimelineModel.FAVORITES_ID).update().toBlocking().first();
                new UserListsModel(account).update().toBlocking().first();
                new DirectsModel(account).update().toBlocking().first();

                List<UserList> userLists = new UserListsModel(account).lists().toBlocking().first();
                for (UserList userList : userLists)
                    new TimelineModel(account, userList.listId()).update().toBlocking().first();

                final int newMentionCount = new TimelineModel(account, TimelineModel.MENTIONS_ID).update().toBlocking().first();
                if (prefsModel.isNotificationsEnabled() && newMentionCount > 0) {
                    notifyMentions(account, newMentionCount);
                }

                final long twoDaysAgo = System.currentTimeMillis() - 2 * 24 * 3600 * 1000;
                Inject.contentResolver().delete(TweetContract.CONTENT_URI,
                        String.format("%s < %d AND %s != %d",
                                TweetContract.CREATED_AT, twoDaysAgo,
                                TweetContract.TIMELINE_ID, TimelineModel.FAVORITES_ID
                        ), null);
            }

            return GcmNetworkManager.RESULT_SUCCESS;
        } catch (Throwable t) {
            Timber.w(t, "");
        }

        return GcmNetworkManager.RESULT_FAILURE;
    }

    private void notifyMentions(final Account account, Integer count) {
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        final Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("account", account);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentIntent(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT));
        builder.setContentText(getString(R.string.new_mentions, count));
        builder.setContentTitle(getString(R.string.app_name));
        builder.setSmallIcon(R.drawable.ic_at);
        builder.setAutoCancel(true);

        if (new PrefsModel().isNotificationSoundEnabled())
            builder.setDefaults(Notification.DEFAULT_SOUND);

        Glide.with(this).load(account.avatar()).asBitmap().into(new SimpleTarget<Bitmap>() {
            @Override
            public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                builder.setLargeIcon(resource);
                notificationManager.notify(account.screenName(), 7226, builder.build());
            }
        });
    }
}
