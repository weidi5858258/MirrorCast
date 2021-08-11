/*
 * Copyright (c) 2010 The Khronos Group Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject
 * to the following conditions:
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

/** OMX_AudioExt.h - OpenMax IL version 1.1.2
 * The OMX_AudioExt header file contains extensions to the
 * definitions used by both the application and the component to
 * access video items.
 */

#ifndef OMX_AudioExt_h
#define OMX_AudioExt_h

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

/* Each OMX header shall include all required header files to allow the
 * header to compile without errors.  The includes below are required
 * for this header file to compile successfully
 */
#include <OMX_Core.h>

#define OMX_AUDIO_AACToolAndroidSSBR (OMX_AUDIO_AACToolVendor << 0) /**< SSBR: MPEG-4 Single-rate (downsampled) Spectral Band Replication tool allowed or active */
#define OMX_AUDIO_AACToolAndroidDSBR (OMX_AUDIO_AACToolVendor << 1) /**< DSBR: MPEG-4 Dual-rate Spectral Band Replication tool allowed or active */

typedef enum OMX_AUDIO_CODINGEXTTYPE {
    OMX_AUDIO_CodingAndroidUnused = OMX_AUDIO_CodingKhronosExtensions + 0x00100000,
    OMX_AUDIO_CodingAndroidAC3,         /**< AC3 encoded data */
    OMX_AUDIO_CodingAndroidOPUS,        /**< OPUS encoded data */
    OMX_AUDIO_CodingAndroidEAC3,        /**< EAC3 encoded data */
    OMX_AUDIO_CodingAndroidAC4,         /**< AC4 encoded data */
    // Mediatek Android Patch Begin, {add additional OMX A/V codec}
    OMX_AUDIO_CodingMPEG,               /**< MP12 encoded data */
    OMX_AUDIO_CodingDTS,                /**< DTS encoded data */
    OMX_AUDIO_CodingAPE,                /**< APE encoded data */
    // Mediatek Android Patch End
} OMX_AUDIO_CODINGEXTTYPE;

typedef struct OMX_AUDIO_PARAM_ANDROID_AC3TYPE {
    OMX_U32 nSize;                 /**< size of the structure in bytes */
    OMX_VERSIONTYPE nVersion;      /**< OMX specification version information */
    OMX_U32 nPortIndex;            /**< port that this structure applies to */
    OMX_U32 nChannels;             /**< Number of channels */
    OMX_U32 nSampleRate;           /**< Sampling rate of the source data.  Use 0 for
                                        variable or unknown sampling rate. */
} OMX_AUDIO_PARAM_ANDROID_AC3TYPE;

typedef struct OMX_AUDIO_PARAM_ANDROID_EAC3TYPE {
    OMX_U32 nSize;                 /**< size of the structure in bytes */
    OMX_VERSIONTYPE nVersion;      /**< OMX specification version information */
    OMX_U32 nPortIndex;            /**< port that this structure applies to */
    OMX_U32 nChannels;             /**< Number of channels */
    OMX_U32 nSampleRate;           /**< Sampling rate of the source data.  Use 0 for
                                        variable or unknown sampling rate. */
} OMX_AUDIO_PARAM_ANDROID_EAC3TYPE;

typedef struct OMX_AUDIO_PARAM_ANDROID_AC4TYPE {
    OMX_U32 nSize;                 /**< size of the structure in bytes */
    OMX_VERSIONTYPE nVersion;      /**< OMX specification version information */
    OMX_U32 nPortIndex;            /**< port that this structure applies to */
    OMX_U32 nChannels;             /**< Number of channels */
    OMX_U32 nSampleRate;           /**< Sampling rate of the source data.  Use 0 for
                                        variable or unknown sampling rate. */
} OMX_AUDIO_PARAM_ANDROID_AC4TYPE;
// Mediatek Android Patch Begin, {add additional OMX A/V codec}
typedef struct OMX_AUDIO_PARAM_ANDROID_DTSTYPE {
    OMX_U32 nSize;                 /**< size of the structure in bytes */
    OMX_VERSIONTYPE nVersion;      /**< OMX specification version information */
    OMX_U32 nPortIndex;            /**< port that this structure applies to */
    OMX_U32 nChannels;             /**< Number of channels */
    OMX_U32 nSampleRate;           /**< Sampling rate of the source data.  Use 0 for
                                        variable or unknown sampling rate. */
} OMX_AUDIO_PARAM_ANDROID_DTSTYPE;

/** MPEG TYPE */
typedef enum OMX_AUDIO_MPEGFORMATTYPE {
  OMX_AUDIO_MPEGFormatUnused = 0, /**< format unused or unknown */
  OMX_AUDIO_MPEGL1,          /**< MPEG format level 1 */
  OMX_AUDIO_MPEGL2,          /**< MPEG format level 2 */
  OMX_AUDIO_MPEGFormatMax = 0x7FFFFFFF
} OMX_AUDIO_MPEGFORMATTYPE;

typedef struct OMX_AUDIO_PARAM_ANDROID_MPEGSTYPE {
    OMX_U32 nSize;            /**< size of the structure in bytes */
    OMX_VERSIONTYPE nVersion; /**< OMX specification version information */
    OMX_U32 nPortIndex;       /**< port that this structure applies to */
    OMX_U32 nChannels;        /**< Number of channels */
    OMX_U32 nSampleRate;      /**< Sampling rate of the source data.  Use 0 for
                                   variable or unknown sampling rate. */
    OMX_AUDIO_MPEGFORMATTYPE eFormat;
} OMX_AUDIO_PARAM_ANDROID_MPEGSTYPE;
// Mediatek Android Patch End

typedef struct OMX_AUDIO_PARAM_ANDROID_OPUSTYPE {
    OMX_U32 nSize;            /**< size of the structure in bytes */
    OMX_VERSIONTYPE nVersion; /**< OMX specification version information */
    OMX_U32 nPortIndex;       /**< port that this structure applies to */
    OMX_U32 nChannels;        /**< Number of channels */
    OMX_U32 nBitRate;         /**< Bit rate of the encoded data data.  Use 0 for variable
                                   rate or unknown bit rates. Encoding is set to the
                                   bitrate closest to specified  value (in bps) */
    OMX_U32 nSampleRate;      /**< Sampling rate of the source data.  Use 0 for
                                   variable or unknown sampling rate. */
    OMX_U32 nAudioBandWidth;  /**< Audio band width (in Hz) to which an encoder should
                                   limit the audio signal. Use 0 to let encoder decide */
} OMX_AUDIO_PARAM_ANDROID_OPUSTYPE;

/** deprecated. use OMX_AUDIO_PARAM_ANDROID_AACDRCPRESENTATIONTYPE */
typedef struct OMX_AUDIO_PARAM_ANDROID_AACPRESENTATIONTYPE {
    OMX_U32 nSize;            /**< size of the structure in bytes */
    OMX_VERSIONTYPE nVersion; /**< OMX specification version information */
    OMX_S32 nMaxOutputChannels;    /**< Maximum channel count to be output, -1 if unspecified, 0 if downmixing disabled */
    OMX_S32 nDrcCut;               /**< The DRC attenuation factor, between 0 and 127, -1 if unspecified */
    OMX_S32 nDrcBoost;             /**< The DRC amplification factor, between 0 and 127, -1 if unspecified */
    OMX_S32 nHeavyCompression;     /**< 0 for light compression, 1 for heavy compression, -1 if unspecified */
    OMX_S32 nTargetReferenceLevel; /**< Target reference level, between 0 and 127, -1 if unspecified */
    OMX_S32 nEncodedTargetLevel;   /**< Target reference level assumed at the encoder, between 0 and 127, -1 if unspecified */
    OMX_S32 nPCMLimiterEnable;     /**< Signal level limiting, 0 for disable, 1 for enable, -1 if unspecified */
} OMX_AUDIO_PARAM_ANDROID_AACPRESENTATIONTYPE;

typedef struct OMX_AUDIO_PARAM_ANDROID_AACDRCPRESENTATIONTYPE {
    OMX_U32 nSize;            /**< size of the structure in bytes */
    OMX_VERSIONTYPE nVersion; /**< OMX specification version information */
    OMX_S32 nMaxOutputChannels;    /**< Maximum channel count to be output, -1 if unspecified, 0 if downmixing disabled */
    OMX_S32 nDrcCut;               /**< The DRC attenuation factor, between 0 and 127, -1 if unspecified */
    OMX_S32 nDrcBoost;             /**< The DRC amplification factor, between 0 and 127, -1 if unspecified */
    OMX_S32 nHeavyCompression;     /**< 0 for light compression, 1 for heavy compression, -1 if unspecified */
    OMX_S32 nTargetReferenceLevel; /**< Target reference level, between 0 and 127, -1 if unspecified */
    OMX_S32 nEncodedTargetLevel;   /**< Target reference level assumed at the encoder, between 0 and 127, -1 if unspecified */
    OMX_S32 nPCMLimiterEnable;     /**< Signal level limiting, 0 for disable, 1 for enable, -1 if unspecified */
    OMX_S32 nDrcEffectType;        /**< MPEG-D DRC effect type, between -1 and 6, -2 if unspecified */
    OMX_S32 nDrcOutputLoudness;    /**< MPEG-D DRC Output Loudness, between -1 and 231, -2 if unspecified */
    OMX_S32 nDrcAlbumMode;         /**< MPEG-D DRC Album Mode, between 0 and 1, -1 if unspecified */
} OMX_AUDIO_PARAM_ANDROID_AACDRCPRESENTATIONTYPE;

typedef struct OMX_AUDIO_PARAM_ANDROID_PROFILETYPE {
   OMX_U32 nSize;
   OMX_VERSIONTYPE nVersion;
   OMX_U32 nPortIndex;
   OMX_U32 eProfile;      /**< type is OMX_AUDIO_AACPROFILETYPE or OMX_AUDIO_WMAPROFILETYPE
                                 depending on context */
   OMX_U32 nProfileIndex; /**< Used to query for individual profile support information */
} OMX_AUDIO_PARAM_ANDROID_PROFILETYPE;

typedef struct OMX_AUDIO_CONFIG_ANDROID_AUDIOPRESENTATION {
    OMX_U32 nSize;                 /**< size of the structure in bytes */
    OMX_VERSIONTYPE nVersion;      /**< OMX specification version information */
    OMX_S32 nPresentationId;       /**< presentation id */
    OMX_S32 nProgramId;            /**< program id */
} OMX_AUDIO_CONFIG_ANDROID_AUDIOPRESENTATION;

// Mediatek Android Patch Begin, {add additional OMX A/V codec}
typedef struct OMX_AUDIO_PARAM_APETYPE {
    OMX_U32 nSize;                 /**< size of the structure in bytes */
    OMX_VERSIONTYPE nVersion;      /**< OMX specification version information */
    OMX_U32 nPortIndex;            /**< port that this structure applies to */
    OMX_U32 nChannels;             /**< Number of channels */
    OMX_U32 nSampleRate;           /**< Sampling rate of the source data.  Use 0 for
                                        variable or unknown sampling rate. */
    OMX_U32 nBitRate;
    OMX_U16 nCompressionType;
    OMX_U16 nBitsPerCodedSample;
    OMX_U32 nBlocksPerFrame;
    OMX_U32 nFinalFrameBlocks;
    OMX_U32 nTotalFrames;
    OMX_S32 nSourceBufferSize;
    OMX_S16 nFileVersion;
} OMX_AUDIO_PARAM_APETYPE;
// Mediatek Android Patch End

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* OMX_AudioExt_h */
/* File EOF */
