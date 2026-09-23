package com.saubh.deskbuddy.server.audio

import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.Guid

/** GUIDs, enum values and vtable slots for the Core Audio + PolicyConfig interfaces we call. */
internal object CoreAudioIds {
    val CLSID_MMDeviceEnumerator = Guid.CLSID("{BCDE0395-E52F-467C-8E3D-C4579291692E}")
    val IID_IMMDeviceEnumerator = Guid.IID("{A95664D2-9614-4F35-A746-DE8DB63617E6}")
    val IID_IAudioEndpointVolume = Guid.IID("{5CDF2C82-841E-4546-9722-0CF74078229A}")
    val CLSID_PolicyConfigClient = Guid.CLSID("{870AF99C-171D-4F9E-AF0D-E63DF40C2BC9}")
    val IID_IPolicyConfig = Guid.IID("{F8679F50-850A-41CF-9C72-430F290290C8}")
    val PKEY_Device_FriendlyName = Guid.GUID("{A45C254E-DF1C-4EFD-8020-67D146A850E0}")
    const val PID_Device_FriendlyName = 14

    const val eRender = 0
    const val eConsole = 0
    const val eMultimedia = 1
    const val DEVICE_STATE_ACTIVE = 1
    const val STGM_READ = 0
    const val CLSCTX_ALL = 23
    const val VT_LPWSTR = 31

    // IMMDeviceEnumerator
    const val ENUM_AUDIO_ENDPOINTS = 3
    const val GET_DEFAULT_AUDIO_ENDPOINT = 4

    // IMMDeviceCollection
    const val COLLECTION_GET_COUNT = 3
    const val COLLECTION_ITEM = 4

    // IMMDevice
    const val DEVICE_ACTIVATE = 3
    const val DEVICE_OPEN_PROPERTY_STORE = 4
    const val DEVICE_GET_ID = 5

    // IPropertyStore
    const val PROPERTY_STORE_GET_VALUE = 5

    // IAudioEndpointVolume
    const val SET_MASTER_VOLUME_SCALAR = 7
    const val GET_MASTER_VOLUME_SCALAR = 9
    const val GET_MUTE = 15

    // IPolicyConfig
    const val SET_DEFAULT_ENDPOINT = 13
}

@Structure.FieldOrder("fmtid", "pid")
internal class PropertyKey(@JvmField var fmtid: Guid.GUID = Guid.GUID(), @JvmField var pid: Int = 0) : Structure()

/** PROPVARIANT (24 bytes on x64); only the VT_LPWSTR shape is read here. */
@Structure.FieldOrder("vt", "reserved1", "reserved2", "reserved3", "pwszVal", "padding")
internal class PropVariant : Structure() {
    @JvmField var vt: Short = 0
    @JvmField var reserved1: Short = 0
    @JvmField var reserved2: Short = 0
    @JvmField var reserved3: Short = 0
    @JvmField var pwszVal: Pointer? = null
    @JvmField var padding: Long = 0
}
