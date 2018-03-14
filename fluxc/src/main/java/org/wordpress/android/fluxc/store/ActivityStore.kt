package org.wordpress.android.fluxc.store

import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.Payload
import org.wordpress.android.fluxc.annotations.action.Action
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.network.BaseRequest
import org.wordpress.android.fluxc.network.rest.wpcom.activity.Activity
import org.wordpress.android.fluxc.network.rest.wpcom.activity.Activity.ActivityError
import org.wordpress.android.fluxc.network.rest.wpcom.activity.ActivityRestClient
import org.wordpress.android.fluxc.network.rest.wpcom.activity.RewindStatus
import org.wordpress.android.fluxc.network.rest.wpcom.activity.RewindStatus.RewindStatusError
import org.wordpress.android.util.AppLog
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityStore
@Inject constructor(private val activityRestClient: ActivityRestClient, val dispatcher: Dispatcher) : Store(dispatcher) {
    @Subscribe(threadMode = ThreadMode.ASYNC)
    override fun onAction(action: Action<*>) {
        val payload = action.payload ?: return
        when (payload) {
            is FetchActivitiesPayload -> fetchActivities(payload)
            is FetchRewindStatePayload -> fetchActivitiesRewind(payload)
        }
    }

    override fun onRegister() {
        AppLog.d(AppLog.T.API, this.javaClass.name + ": onRegister")
    }

    private fun fetchActivities(fetchActivitiesPayload: FetchActivitiesPayload) {
        activityRestClient.fetchActivity(fetchActivitiesPayload.site, fetchActivitiesPayload.number, fetchActivitiesPayload.offset)
    }

    private fun fetchActivitiesRewind(fetchActivitiesRewindPayload: FetchRewindStatePayload) {
        activityRestClient.fetchActivityRewind(fetchActivitiesRewindPayload.site, fetchActivitiesRewindPayload.number, fetchActivitiesRewindPayload.offset)
    }

    // Payloads
    data class FetchActivitiesPayload(val site: SiteModel,
                                      val number: Int,
                                      val offset: Int) : Payload<BaseRequest.BaseNetworkError>()

    data class FetchRewindStatePayload(val site: SiteModel,
                                       val number: Int,
                                       val offset: Int) : Payload<BaseRequest.BaseNetworkError>()

    data class FetchedActivitiesPayload(val activityResponses: List<Activity> = listOf(),
                                        val site: SiteModel,
                                        val number: Int,
                                        val offset: Int) : Payload<ActivityError>() {
        constructor(error: ActivityError,
                    site: SiteModel,
                    number: Int,
                    offset: Int) : this(site = site, number = number, offset = offset) {
            this.error = error
        }
    }

    data class FetchRewindStateResponsePayload(val rewindStatusResponse: RewindStatus? = null,
                                               val site: SiteModel,
                                               val number: Int,
                                               val offset: Int) : Payload<RewindStatusError>() {
        constructor(error: RewindStatusError,
                    site: SiteModel,
                    number: Int,
                    offset: Int) : this(site = site, number = number, offset = offset) {
            this.error = error
        }
    }
}
