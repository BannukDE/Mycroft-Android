/*
 *  Copyright (c) 2017. Mycroft AI, Inc.
 *
 *  This file is part of Mycroft-Android a client for Mycroft Core.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package mycroft.ai.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Created by paul on 2016/06/22.
 */

object NetworkUtil {

    private var TYPE_WIFI = 1
    private var TYPE_MOBILE = 2
    private var TYPE_NOT_CONNECTED = 0
    var NETWORK_STATUS_NOT_CONNECTED = 0
    var NETWORK_STATUS_WIFI = 1
    private var NETWORK_STATUS_MOBILE = 2

    fun getConnectivityStatus(context: Context): Int {

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            val activeNetwork = cm.activeNetworkInfo
            if (null != activeNetwork) {
                if (activeNetwork.type == ConnectivityManager.TYPE_WIFI)
                    return TYPE_WIFI

                if (activeNetwork.type == ConnectivityManager.TYPE_MOBILE)
                    return TYPE_MOBILE
            }
        } else {
            val activeNetwork = cm.activeNetwork
            if (null != activeNetwork) {
                val activeNetworkAble = cm.getNetworkCapabilities(activeNetwork)
                if (activeNetworkAble.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || activeNetworkAble.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
                    return TYPE_WIFI

                if (activeNetworkAble.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                    return TYPE_MOBILE
            }
        }
        return TYPE_NOT_CONNECTED
    }

    fun getConnectivityStatusString(context: Context): Int {
        val conn = NetworkUtil.getConnectivityStatus(context)
        var status = 0

        when (conn) {
            NetworkUtil.TYPE_WIFI -> {
                status = NETWORK_STATUS_WIFI
            }
            NetworkUtil.TYPE_MOBILE -> {
                status = NETWORK_STATUS_MOBILE
            }
            NetworkUtil.TYPE_NOT_CONNECTED -> {
                status = NETWORK_STATUS_NOT_CONNECTED
            }
        }
        return status
    }
}