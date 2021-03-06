package com.habitrpg.android.habitica.helpers.notifications

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.RemoteMessage
import com.habitrpg.android.habitica.data.ApiClient
import com.habitrpg.android.habitica.helpers.AmplitudeManager
import com.habitrpg.android.habitica.helpers.RxErrorHandler
import com.habitrpg.android.habitica.models.user.User
import io.reactivex.functions.Consumer
import java.util.*

class PushNotificationManager(var apiClient: ApiClient, private val sharedPreferences: SharedPreferences, private val context: Context) {

    var refreshedToken: String = ""
    set(value) {
        if (value.isEmpty()) {
            return
        }

        field = value
        val editor = sharedPreferences.edit()
        editor.putString(DEVICE_TOKEN_PREFERENCE_KEY, value)
        editor.apply()
    }
    private var user: User? = null

    fun setUser(user: User) {
        this.user = user
    }

    //@TODO: Use preferences
    fun addPushDeviceUsingStoredToken() {
        if (this.refreshedToken.isEmpty()) {
            this.refreshedToken = FirebaseInstanceId.getInstance().token ?: ""
        }

        if (this.refreshedToken.isEmpty() || this.user == null || this.userHasPushDevice() || !this.userIsSubscribedToNotifications()) {
            return
        }

        val pushDeviceData = HashMap<String, String>()
        pushDeviceData["regId"] = this.refreshedToken
        pushDeviceData["type"] = "android"
        apiClient.addPushDevice(pushDeviceData).subscribe(Consumer { }, RxErrorHandler.handleEmptyError())
    }

    fun removePushDeviceUsingStoredToken() {
        if (this.refreshedToken.isEmpty()) {
            return
        }
        apiClient.deletePushDevice(this.refreshedToken).subscribe(Consumer { }, RxErrorHandler.handleEmptyError())
    }

    private fun userHasPushDevice(): Boolean {
        if (this.user?.pushDevices == null) {
            return true
        }

        for (pushDevice in this.user?.pushDevices ?: emptyList()) {
            if (pushDevice.regId == this.refreshedToken) {
                return true
            }
        }
        return false
    }

    fun displayNotification(remoteMessage: RemoteMessage) {
        val remoteMessageIdentifier = remoteMessage.data["identifier"]


        val notificationFactory = HabiticaLocalNotificationFactory()
        val notification = notificationFactory.build(remoteMessageIdentifier, context)
        if (userIsSubscribedToNotificationType(remoteMessageIdentifier) && notification != null) {
            val additionalData = HashMap<String, Any>()
            additionalData["identifier"] = remoteMessageIdentifier ?: ""
            AmplitudeManager.sendEvent("receive notification", AmplitudeManager.EVENT_CATEGORY_BEHAVIOUR, AmplitudeManager.EVENT_HITTYPE_EVENT, additionalData)
            notification.setExtras(remoteMessage.data)
            notification.notifyLocally(remoteMessage.data["title"], remoteMessage.data["body"])
        }
    }

    private fun userIsSubscribedToNotifications(): Boolean {
        return sharedPreferences.getBoolean("pushNotifications", true)
    }

    private fun userIsSubscribedToNotificationType(type: String?): Boolean {
        var key = ""

        //@TODO: If user has push turned off to send

        if (type == null) {
            return true
        }

        when {
            type == PARTY_INVITE_PUSH_NOTIFICATION_KEY -> key = "preference_push_invited_to_party"
            type.contains(RECEIVED_PRIVATE_MESSAGE_PUSH_NOTIFICATION_KEY) -> key = "preference_push_received_a_private_message"
            type.contains(RECEIVED_GEMS_PUSH_NOTIFICATION_KEY) -> key = "preference_push_gifted_gems"
            type.contains(RECEIVED_SUBSCRIPTION_GIFT_PUSH_NOTIFICATION_KEY) -> key = "preference_push_gifted_subscription"
            type.contains(GUILD_INVITE_PUSH_NOTIFICATION_KEY) -> key = "preference_push_invited_to_guild"
            type.contains(QUEST_INVITE_PUSH_NOTIFICATION_KEY) -> key = "preference_push_invited_to_quest"
            type.contains(QUEST_BEGUN_PUSH_NOTIFICATION_KEY) -> key = "preference_push_your_quest_has_begun"
            type.contains(WON_CHALLENGE_PUSH_NOTIFICATION_KEY) -> key = "preference_push_you_won_challenge"
        }

        return sharedPreferences.getBoolean(key, true)
    }

    companion object {

        const val PARTY_INVITE_PUSH_NOTIFICATION_KEY = "invitedParty"
        const val RECEIVED_PRIVATE_MESSAGE_PUSH_NOTIFICATION_KEY = "newPM"
        const val RECEIVED_GEMS_PUSH_NOTIFICATION_KEY = "giftedGems"
        const val RECEIVED_SUBSCRIPTION_GIFT_PUSH_NOTIFICATION_KEY = "giftedSubscription"
        const val GUILD_INVITE_PUSH_NOTIFICATION_KEY = "invitedGuild"
        const val QUEST_INVITE_PUSH_NOTIFICATION_KEY = "questInvitation"
        const val QUEST_BEGUN_PUSH_NOTIFICATION_KEY = "questStarted"
        const val WON_CHALLENGE_PUSH_NOTIFICATION_KEY = "wonChallenge"
        const val CHANGE_USERNAME_PUSH_NOTIFICATION_KEY = "changeUsername"
        private const val DEVICE_TOKEN_PREFERENCE_KEY = "device-token-preference"
    }
}
