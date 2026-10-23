package com.swaystack.app

import android.app.Activity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/** Offline-safe AdMob setup. All identifiers are Google's official test ids. */
class AdsManager(private val activity: Activity) {
    private var rewarded: RewardedAd? = null
    private var initialized = false

    fun requestConsentAndInitialize() {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ -> loadAdsIfAllowed(info) }
        }, { loadAdsIfAllowed(info) })
    }

    private fun loadAdsIfAllowed(info: ConsentInformation) {
        if (!info.canRequestAds) return
        MobileAds.initialize(activity) { initialized = true }
        RewardedAd.load(activity, BuildConfig.ADMOB_REWARDED_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewarded = ad }
        })
    }

    fun showRewarded(onReward: () -> Unit): Boolean {
        val ad = rewarded ?: return false
        ad.show(activity) { onReward() }
        rewarded = null
        return true
    }
}
