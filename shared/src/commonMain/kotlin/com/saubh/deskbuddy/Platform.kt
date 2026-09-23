package com.saubh.deskbuddy

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform