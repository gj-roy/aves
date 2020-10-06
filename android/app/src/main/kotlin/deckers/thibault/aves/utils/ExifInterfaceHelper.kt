package deckers.thibault.aves.utils

import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.drew.lang.Rational
import com.drew.metadata.Directory
import com.drew.metadata.exif.*
import com.drew.metadata.exif.makernotes.OlympusCameraSettingsMakernoteDirectory
import com.drew.metadata.exif.makernotes.OlympusImageProcessingMakernoteDirectory
import com.drew.metadata.exif.makernotes.OlympusMakernoteDirectory
import java.util.*
import kotlin.math.floor
import kotlin.math.roundToLong

object ExifInterfaceHelper {
    private val LOG_TAG = Utils.createLogTag(ExifInterfaceHelper::class.java)

    private val exifIFD0Dir = ExifIFD0Directory()
    private val exifThumbnailDirectory = ExifThumbnailDirectory()
    private val gpsDir = GpsDirectory()
    private val olympusImageProcessingMakernoteDirectory = OlympusImageProcessingMakernoteDirectory()
    private val olympusCameraSettingsMakernoteDirectory = OlympusCameraSettingsMakernoteDirectory()
    private val olympusMakernoteDirectory = OlympusMakernoteDirectory()
    private val panasonicRawIFD0Directory = PanasonicRawIFD0Directory()

    // ExifInterface always states it has the following attributes
    // and returns "0" instead of "null" when they are actually missing
    private val neverNullTags = listOf(
            ExifInterface.TAG_IMAGE_LENGTH,
            ExifInterface.TAG_IMAGE_WIDTH,
            ExifInterface.TAG_LIGHT_SOURCE,
            ExifInterface.TAG_ORIENTATION,
    )

    private val baseTags: Map<String, TagMapper?> = hashMapOf(
            ExifInterface.TAG_APERTURE_VALUE to TagMapper(ExifDirectoryBase.TAG_APERTURE, exifIFD0Dir, TagFormat.RATIONAL),
            ExifInterface.TAG_ARTIST to TagMapper(ExifDirectoryBase.TAG_ARTIST, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_BITS_PER_SAMPLE to TagMapper(ExifDirectoryBase.TAG_BITS_PER_SAMPLE, exifIFD0Dir, TagFormat.SHORT),
            ExifInterface.TAG_BODY_SERIAL_NUMBER to TagMapper(ExifDirectoryBase.TAG_BODY_SERIAL_NUMBER, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_BRIGHTNESS_VALUE to TagMapper(ExifDirectoryBase.TAG_BRIGHTNESS_VALUE, exifIFD0Dir, TagFormat.RATIONAL),
            ExifInterface.TAG_CAMERA_OWNER_NAME to TagMapper(ExifDirectoryBase.TAG_CAMERA_OWNER_NAME, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_CFA_PATTERN to TagMapper(ExifDirectoryBase.TAG_CFA_PATTERN, exifIFD0Dir, TagFormat.UNDEFINED),
            ExifInterface.TAG_COLOR_SPACE to TagMapper(ExifDirectoryBase.TAG_COLOR_SPACE, exifIFD0Dir, TagFormat.SHORT),
            ExifInterface.TAG_COMPONENTS_CONFIGURATION to TagMapper(ExifDirectoryBase.TAG_COMPONENTS_CONFIGURATION, exifIFD0Dir, TagFormat.UNDEFINED),
            ExifInterface.TAG_COMPRESSED_BITS_PER_PIXEL to TagMapper(ExifDirectoryBase.TAG_COMPRESSED_AVERAGE_BITS_PER_PIXEL, exifIFD0Dir, TagFormat.RATIONAL),
            ExifInterface.TAG_COMPRESSION to TagMapper(ExifDirectoryBase.TAG_COMPRESSION, exifIFD0Dir, null),
            ExifInterface.TAG_CONTRAST to TagMapper(ExifDirectoryBase.TAG_CONTRAST, exifIFD0Dir, null),
            ExifInterface.TAG_COPYRIGHT to TagMapper(ExifDirectoryBase.TAG_COPYRIGHT, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_CUSTOM_RENDERED to TagMapper(ExifDirectoryBase.TAG_CUSTOM_RENDERED, exifIFD0Dir, null),
            ExifInterface.TAG_DATETIME to TagMapper(ExifDirectoryBase.TAG_DATETIME, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_DATETIME_DIGITIZED to TagMapper(ExifDirectoryBase.TAG_DATETIME_DIGITIZED, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_DATETIME_ORIGINAL to TagMapper(ExifDirectoryBase.TAG_DATETIME_ORIGINAL, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION to TagMapper(ExifDirectoryBase.TAG_DEVICE_SETTING_DESCRIPTION, exifIFD0Dir, null),
            ExifInterface.TAG_DIGITAL_ZOOM_RATIO to TagMapper(ExifDirectoryBase.TAG_DIGITAL_ZOOM_RATIO, exifIFD0Dir, TagFormat.RATIONAL),
            ExifInterface.TAG_EXIF_VERSION to TagMapper(ExifDirectoryBase.TAG_EXIF_VERSION, exifIFD0Dir, TagFormat.UNDEFINED),
            ExifInterface.TAG_EXPOSURE_BIAS_VALUE to TagMapper(ExifDirectoryBase.TAG_EXPOSURE_BIAS, exifIFD0Dir, TagFormat.RATIONAL),
            ExifInterface.TAG_EXPOSURE_INDEX to TagMapper(ExifDirectoryBase.TAG_EXPOSURE_INDEX, exifIFD0Dir, null),
            ExifInterface.TAG_EXPOSURE_MODE to TagMapper(ExifDirectoryBase.TAG_EXPOSURE_MODE, exifIFD0Dir, TagFormat.SHORT),
            ExifInterface.TAG_EXPOSURE_PROGRAM to TagMapper(ExifDirectoryBase.TAG_EXPOSURE_PROGRAM, exifIFD0Dir, TagFormat.SHORT),
            ExifInterface.TAG_EXPOSURE_TIME to TagMapper(ExifDirectoryBase.TAG_EXPOSURE_TIME, exifIFD0Dir, TagFormat.RATIONAL),
            ExifInterface.TAG_FILE_SOURCE to TagMapper(ExifDirectoryBase.TAG_FILE_SOURCE, exifIFD0Dir, null),
            ExifInterface.TAG_FLASH to TagMapper(ExifDirectoryBase.TAG_FLASH, exifIFD0Dir, TagFormat.SHORT),
            ExifInterface.TAG_FLASHPIX_VERSION to TagMapper(ExifDirectoryBase.TAG_FLASHPIX_VERSION, exifIFD0Dir, TagFormat.UNDEFINED),
            ExifInterface.TAG_FLASH_ENERGY to TagMapper(ExifDirectoryBase.TAG_FLASH_ENERGY, exifIFD0Dir, TagFormat.RATIONAL),
            ExifInterface.TAG_FOCAL_LENGTH to TagMapper(ExifDirectoryBase.TAG_FOCAL_LENGTH, exifIFD0Dir, TagFormat.RATIONAL),
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM to TagMapper(ExifDirectoryBase.TAG_35MM_FILM_EQUIV_FOCAL_LENGTH, exifIFD0Dir, TagFormat.SHORT),
            ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT to TagMapper(ExifDirectoryBase.TAG_FOCAL_PLANE_RESOLUTION_UNIT, exifIFD0Dir, null),
            ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION to TagMapper(ExifDirectoryBase.TAG_FOCAL_PLANE_X_RESOLUTION, exifIFD0Dir, null),
            ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION to TagMapper(ExifDirectoryBase.TAG_FOCAL_PLANE_Y_RESOLUTION, exifIFD0Dir, null),
            ExifInterface.TAG_F_NUMBER to TagMapper(ExifDirectoryBase.TAG_FNUMBER, exifIFD0Dir, TagFormat.RATIONAL),
            ExifInterface.TAG_GAIN_CONTROL to TagMapper(ExifDirectoryBase.TAG_GAIN_CONTROL, exifIFD0Dir, null),
            ExifInterface.TAG_GAMMA to TagMapper(ExifDirectoryBase.TAG_GAMMA, exifIFD0Dir, TagFormat.RATIONAL),
            ExifInterface.TAG_IMAGE_DESCRIPTION to TagMapper(ExifDirectoryBase.TAG_IMAGE_DESCRIPTION, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_IMAGE_LENGTH to TagMapper(ExifDirectoryBase.TAG_IMAGE_HEIGHT, exifIFD0Dir, TagFormat.LONG),
            ExifInterface.TAG_IMAGE_UNIQUE_ID to TagMapper(ExifDirectoryBase.TAG_IMAGE_UNIQUE_ID, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_IMAGE_WIDTH to TagMapper(ExifDirectoryBase.TAG_IMAGE_WIDTH, exifIFD0Dir, TagFormat.LONG),
            ExifInterface.TAG_INTEROPERABILITY_INDEX to TagMapper(ExifDirectoryBase.TAG_INTEROP_INDEX, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_ISO_SPEED to TagMapper(ExifDirectoryBase.TAG_ISO_SPEED, exifIFD0Dir, null),
            ExifInterface.TAG_ISO_SPEED_LATITUDE_YYY to TagMapper(ExifDirectoryBase.TAG_ISO_SPEED_LATITUDE_YYY, exifIFD0Dir, TagFormat.LONG),
            ExifInterface.TAG_ISO_SPEED_LATITUDE_ZZZ to TagMapper(ExifDirectoryBase.TAG_ISO_SPEED_LATITUDE_ZZZ, exifIFD0Dir, TagFormat.LONG),
            ExifInterface.TAG_LENS_MAKE to TagMapper(ExifDirectoryBase.TAG_LENS_MAKE, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_LENS_MODEL to TagMapper(ExifDirectoryBase.TAG_LENS_MODEL, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_LENS_SERIAL_NUMBER to TagMapper(ExifDirectoryBase.TAG_LENS_SERIAL_NUMBER, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_LENS_SPECIFICATION to TagMapper(ExifDirectoryBase.TAG_LENS_SPECIFICATION, exifIFD0Dir, TagFormat.RATIONAL_ARRAY),
            ExifInterface.TAG_LIGHT_SOURCE to TagMapper(ExifDirectoryBase.TAG_WHITE_BALANCE, exifIFD0Dir, null),
            ExifInterface.TAG_MAKE to TagMapper(ExifDirectoryBase.TAG_MAKE, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_MAKER_NOTE to TagMapper(ExifDirectoryBase.TAG_MAKERNOTE, exifIFD0Dir, null),
            ExifInterface.TAG_MAX_APERTURE_VALUE to TagMapper(ExifDirectoryBase.TAG_MAX_APERTURE, exifIFD0Dir, TagFormat.RATIONAL),
            ExifInterface.TAG_METERING_MODE to TagMapper(ExifDirectoryBase.TAG_METERING_MODE, exifIFD0Dir, TagFormat.SHORT),
            ExifInterface.TAG_MODEL to TagMapper(ExifDirectoryBase.TAG_MODEL, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_NEW_SUBFILE_TYPE to TagMapper(ExifDirectoryBase.TAG_NEW_SUBFILE_TYPE, exifIFD0Dir, TagFormat.LONG),
            ExifInterface.TAG_OECF to TagMapper(ExifDirectoryBase.TAG_OPTO_ELECTRIC_CONVERSION_FUNCTION, exifIFD0Dir, null),
            ExifInterface.TAG_OFFSET_TIME to TagMapper(ExifDirectoryBase.TAG_TIME_ZONE, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_OFFSET_TIME_DIGITIZED to TagMapper(ExifDirectoryBase.TAG_TIME_ZONE_DIGITIZED, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL to TagMapper(ExifDirectoryBase.TAG_TIME_ZONE_ORIGINAL, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_ORIENTATION to TagMapper(ExifDirectoryBase.TAG_ORIENTATION, exifIFD0Dir, TagFormat.SHORT),
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY to TagMapper(ExifDirectoryBase.TAG_ISO_EQUIVALENT, exifIFD0Dir, TagFormat.SHORT),
            ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION to TagMapper(ExifDirectoryBase.TAG_PHOTOMETRIC_INTERPRETATION, exifIFD0Dir, null),
            ExifInterface.TAG_PIXEL_X_DIMENSION to TagMapper(ExifDirectoryBase.TAG_EXIF_IMAGE_WIDTH, exifIFD0Dir, TagFormat.LONG),
            ExifInterface.TAG_PIXEL_Y_DIMENSION to TagMapper(ExifDirectoryBase.TAG_EXIF_IMAGE_HEIGHT, exifIFD0Dir, TagFormat.LONG),
            ExifInterface.TAG_PLANAR_CONFIGURATION to TagMapper(ExifDirectoryBase.TAG_PLANAR_CONFIGURATION, exifIFD0Dir, null),
            ExifInterface.TAG_PRIMARY_CHROMATICITIES to TagMapper(ExifDirectoryBase.TAG_PRIMARY_CHROMATICITIES, exifIFD0Dir, null),
            ExifInterface.TAG_RECOMMENDED_EXPOSURE_INDEX to TagMapper(ExifDirectoryBase.TAG_RECOMMENDED_EXPOSURE_INDEX, exifIFD0Dir, null),
            ExifInterface.TAG_REFERENCE_BLACK_WHITE to TagMapper(ExifDirectoryBase.TAG_REFERENCE_BLACK_WHITE, exifIFD0Dir, null),
            ExifInterface.TAG_RELATED_SOUND_FILE to TagMapper(ExifDirectoryBase.TAG_RELATED_SOUND_FILE, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_RESOLUTION_UNIT to TagMapper(ExifDirectoryBase.TAG_RESOLUTION_UNIT, exifIFD0Dir, TagFormat.SHORT),
            ExifInterface.TAG_ROWS_PER_STRIP to TagMapper(ExifDirectoryBase.TAG_ROWS_PER_STRIP, exifIFD0Dir, null),
            ExifInterface.TAG_SAMPLES_PER_PIXEL to TagMapper(ExifDirectoryBase.TAG_SAMPLES_PER_PIXEL, exifIFD0Dir, null),
            ExifInterface.TAG_SATURATION to TagMapper(ExifDirectoryBase.TAG_SATURATION, exifIFD0Dir, null),
            ExifInterface.TAG_SCENE_CAPTURE_TYPE to TagMapper(ExifDirectoryBase.TAG_SCENE_CAPTURE_TYPE, exifIFD0Dir, TagFormat.SHORT),
            ExifInterface.TAG_SCENE_TYPE to TagMapper(ExifDirectoryBase.TAG_SCENE_TYPE, exifIFD0Dir, TagFormat.UNDEFINED),
            ExifInterface.TAG_SENSING_METHOD to TagMapper(ExifDirectoryBase.TAG_SENSING_METHOD, exifIFD0Dir, null),
            ExifInterface.TAG_SENSITIVITY_TYPE to TagMapper(ExifDirectoryBase.TAG_SENSITIVITY_TYPE, exifIFD0Dir, null),
            ExifInterface.TAG_SHARPNESS to TagMapper(ExifDirectoryBase.TAG_SHARPNESS, exifIFD0Dir, null),
            ExifInterface.TAG_SHUTTER_SPEED_VALUE to TagMapper(ExifDirectoryBase.TAG_SHUTTER_SPEED, exifIFD0Dir, null),
            ExifInterface.TAG_SOFTWARE to TagMapper(ExifDirectoryBase.TAG_SOFTWARE, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_SPATIAL_FREQUENCY_RESPONSE to TagMapper(ExifDirectoryBase.TAG_SPATIAL_FREQ_RESPONSE, exifIFD0Dir, null),
            ExifInterface.TAG_SPECTRAL_SENSITIVITY to TagMapper(ExifDirectoryBase.TAG_SPECTRAL_SENSITIVITY, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_STANDARD_OUTPUT_SENSITIVITY to TagMapper(ExifDirectoryBase.TAG_STANDARD_OUTPUT_SENSITIVITY, exifIFD0Dir, null),
            ExifInterface.TAG_STRIP_BYTE_COUNTS to TagMapper(ExifDirectoryBase.TAG_STRIP_BYTE_COUNTS, exifIFD0Dir, TagFormat.LONG),
            ExifInterface.TAG_STRIP_OFFSETS to TagMapper(ExifDirectoryBase.TAG_STRIP_OFFSETS, exifIFD0Dir, TagFormat.LONG),
            ExifInterface.TAG_SUBFILE_TYPE to TagMapper(ExifDirectoryBase.TAG_SUBFILE_TYPE, exifIFD0Dir, TagFormat.SHORT),
            ExifInterface.TAG_SUBJECT_AREA to TagMapper(ExifDirectoryBase.TAG_SUBJECT_LOCATION_TIFF_EP, exifIFD0Dir, TagFormat.SHORT),
            ExifInterface.TAG_SUBJECT_DISTANCE to TagMapper(ExifDirectoryBase.TAG_SUBJECT_DISTANCE, exifIFD0Dir, TagFormat.RATIONAL),
            ExifInterface.TAG_SUBJECT_DISTANCE_RANGE to TagMapper(ExifDirectoryBase.TAG_SUBJECT_DISTANCE_RANGE, exifIFD0Dir, TagFormat.SHORT),
            ExifInterface.TAG_SUBJECT_LOCATION to TagMapper(ExifDirectoryBase.TAG_SUBJECT_LOCATION, exifIFD0Dir, TagFormat.SHORT),
            ExifInterface.TAG_SUBSEC_TIME to TagMapper(ExifDirectoryBase.TAG_SUBSECOND_TIME, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED to TagMapper(ExifDirectoryBase.TAG_SUBSECOND_TIME_DIGITIZED, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL to TagMapper(ExifDirectoryBase.TAG_SUBSECOND_TIME_ORIGINAL, exifIFD0Dir, TagFormat.ASCII),
            ExifInterface.TAG_THUMBNAIL_IMAGE_LENGTH to TagMapper(ExifDirectoryBase.TAG_IMAGE_HEIGHT, exifIFD0Dir, TagFormat.LONG), // IFD_THUMBNAIL_TAGS 0x0101
            ExifInterface.TAG_THUMBNAIL_IMAGE_WIDTH to TagMapper(ExifDirectoryBase.TAG_IMAGE_WIDTH, exifIFD0Dir, TagFormat.LONG), // IFD_THUMBNAIL_TAGS 0x0100
            ExifInterface.TAG_TRANSFER_FUNCTION to TagMapper(ExifDirectoryBase.TAG_TRANSFER_FUNCTION, exifIFD0Dir, TagFormat.SHORT),
            ExifInterface.TAG_USER_COMMENT to TagMapper(ExifDirectoryBase.TAG_USER_COMMENT, exifIFD0Dir, TagFormat.COMMENT),
            ExifInterface.TAG_WHITE_BALANCE to TagMapper(ExifDirectoryBase.TAG_WHITE_BALANCE, exifIFD0Dir, TagFormat.SHORT),
            ExifInterface.TAG_WHITE_POINT to TagMapper(ExifDirectoryBase.TAG_WHITE_POINT, exifIFD0Dir, TagFormat.RATIONAL),
            ExifInterface.TAG_X_RESOLUTION to TagMapper(ExifDirectoryBase.TAG_X_RESOLUTION, exifIFD0Dir, TagFormat.RATIONAL),
            ExifInterface.TAG_Y_CB_CR_COEFFICIENTS to TagMapper(ExifDirectoryBase.TAG_YCBCR_COEFFICIENTS, exifIFD0Dir, TagFormat.RATIONAL),
            ExifInterface.TAG_Y_CB_CR_POSITIONING to TagMapper(ExifDirectoryBase.TAG_YCBCR_POSITIONING, exifIFD0Dir, TagFormat.SHORT),
            ExifInterface.TAG_Y_CB_CR_SUB_SAMPLING to TagMapper(ExifDirectoryBase.TAG_YCBCR_SUBSAMPLING, exifIFD0Dir, TagFormat.SHORT),
            ExifInterface.TAG_Y_RESOLUTION to TagMapper(ExifDirectoryBase.TAG_Y_RESOLUTION, exifIFD0Dir, TagFormat.RATIONAL),
    )

    private val thumbnailTags: Map<String, TagMapper?> = hashMapOf(
            ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT to TagMapper(ExifThumbnailDirectory.TAG_THUMBNAIL_OFFSET, exifThumbnailDirectory, TagFormat.LONG), // IFD_TIFF_TAGS or IFD_THUMBNAIL_TAGS 0x0201
            ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH to TagMapper(ExifThumbnailDirectory.TAG_THUMBNAIL_LENGTH, exifThumbnailDirectory, TagFormat.LONG), // IFD_TIFF_TAGS or IFD_THUMBNAIL_TAGS 0x0202
    )

    private val gpsTags: Map<String, TagMapper?> = hashMapOf(
            // GPS
            ExifInterface.TAG_GPS_ALTITUDE to TagMapper(GpsDirectory.TAG_ALTITUDE, gpsDir, TagFormat.RATIONAL),
            ExifInterface.TAG_GPS_ALTITUDE_REF to TagMapper(GpsDirectory.TAG_ALTITUDE_REF, gpsDir, TagFormat.BYTE),
            ExifInterface.TAG_GPS_AREA_INFORMATION to TagMapper(GpsDirectory.TAG_AREA_INFORMATION, gpsDir, TagFormat.COMMENT),
            ExifInterface.TAG_GPS_DATESTAMP to TagMapper(GpsDirectory.TAG_DATE_STAMP, gpsDir, TagFormat.ASCII),
            ExifInterface.TAG_GPS_DEST_BEARING to TagMapper(GpsDirectory.TAG_DEST_BEARING, gpsDir, TagFormat.RATIONAL),
            ExifInterface.TAG_GPS_DEST_BEARING_REF to TagMapper(GpsDirectory.TAG_DEST_BEARING_REF, gpsDir, TagFormat.ASCII),
            ExifInterface.TAG_GPS_DEST_DISTANCE to TagMapper(GpsDirectory.TAG_DEST_DISTANCE, gpsDir, TagFormat.RATIONAL),
            ExifInterface.TAG_GPS_DEST_DISTANCE_REF to TagMapper(GpsDirectory.TAG_DEST_DISTANCE_REF, gpsDir, TagFormat.ASCII),
            ExifInterface.TAG_GPS_DEST_LATITUDE to TagMapper(GpsDirectory.TAG_DEST_LATITUDE, gpsDir, TagFormat.RATIONAL_ARRAY),
            ExifInterface.TAG_GPS_DEST_LATITUDE_REF to TagMapper(GpsDirectory.TAG_DEST_LATITUDE_REF, gpsDir, TagFormat.ASCII),
            ExifInterface.TAG_GPS_DEST_LONGITUDE to TagMapper(GpsDirectory.TAG_DEST_LONGITUDE, gpsDir, TagFormat.RATIONAL_ARRAY),
            ExifInterface.TAG_GPS_DEST_LONGITUDE_REF to TagMapper(GpsDirectory.TAG_DEST_LONGITUDE_REF, gpsDir, TagFormat.ASCII),
            ExifInterface.TAG_GPS_DIFFERENTIAL to TagMapper(GpsDirectory.TAG_DIFFERENTIAL, gpsDir, TagFormat.SHORT),
            ExifInterface.TAG_GPS_DOP to TagMapper(GpsDirectory.TAG_DOP, gpsDir, TagFormat.RATIONAL),
            ExifInterface.TAG_GPS_H_POSITIONING_ERROR to TagMapper(GpsDirectory.TAG_H_POSITIONING_ERROR, gpsDir, TagFormat.RATIONAL),
            ExifInterface.TAG_GPS_IMG_DIRECTION to TagMapper(GpsDirectory.TAG_IMG_DIRECTION, gpsDir, TagFormat.RATIONAL),
            ExifInterface.TAG_GPS_IMG_DIRECTION_REF to TagMapper(GpsDirectory.TAG_IMG_DIRECTION_REF, gpsDir, TagFormat.ASCII),
            ExifInterface.TAG_GPS_LATITUDE to TagMapper(GpsDirectory.TAG_LATITUDE, gpsDir, TagFormat.RATIONAL_ARRAY),
            ExifInterface.TAG_GPS_LATITUDE_REF to TagMapper(GpsDirectory.TAG_LATITUDE_REF, gpsDir, TagFormat.ASCII),
            ExifInterface.TAG_GPS_LONGITUDE to TagMapper(GpsDirectory.TAG_LONGITUDE, gpsDir, TagFormat.RATIONAL_ARRAY),
            ExifInterface.TAG_GPS_LONGITUDE_REF to TagMapper(GpsDirectory.TAG_LONGITUDE_REF, gpsDir, TagFormat.ASCII),
            ExifInterface.TAG_GPS_MAP_DATUM to TagMapper(GpsDirectory.TAG_MAP_DATUM, gpsDir, TagFormat.ASCII),
            ExifInterface.TAG_GPS_MEASURE_MODE to TagMapper(GpsDirectory.TAG_MEASURE_MODE, gpsDir, TagFormat.ASCII),
            ExifInterface.TAG_GPS_PROCESSING_METHOD to TagMapper(GpsDirectory.TAG_PROCESSING_METHOD, gpsDir, TagFormat.COMMENT),
            ExifInterface.TAG_GPS_SATELLITES to TagMapper(GpsDirectory.TAG_SATELLITES, gpsDir, TagFormat.ASCII),
            ExifInterface.TAG_GPS_SPEED to TagMapper(GpsDirectory.TAG_SPEED, gpsDir, TagFormat.RATIONAL),
            ExifInterface.TAG_GPS_SPEED_REF to TagMapper(GpsDirectory.TAG_SPEED_REF, gpsDir, TagFormat.ASCII),
            ExifInterface.TAG_GPS_STATUS to TagMapper(GpsDirectory.TAG_STATUS, gpsDir, TagFormat.ASCII),
            ExifInterface.TAG_GPS_TIMESTAMP to TagMapper(GpsDirectory.TAG_TIME_STAMP, gpsDir, TagFormat.RATIONAL_ARRAY),
            ExifInterface.TAG_GPS_TRACK to TagMapper(GpsDirectory.TAG_TRACK, gpsDir, TagFormat.RATIONAL),
            ExifInterface.TAG_GPS_TRACK_REF to TagMapper(GpsDirectory.TAG_TRACK_REF, gpsDir, TagFormat.ASCII),
            ExifInterface.TAG_GPS_VERSION_ID to TagMapper(GpsDirectory.TAG_VERSION_ID, gpsDir, TagFormat.BYTE),
    )

    private val xmpTags: Map<String, TagMapper?> = hashMapOf(
            ExifInterface.TAG_XMP to null, // IFD_TIFF_TAGS 0x02BC
    )

    private val rawTags: Map<String, TagMapper?> = hashMapOf(
            // DNG
            ExifInterface.TAG_DEFAULT_CROP_SIZE to null, // IFD_EXIF_TAGS 0xC620
            ExifInterface.TAG_DNG_VERSION to null, // IFD_EXIF_TAGS 0xC612
            // ORF
            ExifInterface.TAG_ORF_ASPECT_FRAME to TagMapper(OlympusImageProcessingMakernoteDirectory.TagAspectFrame, olympusImageProcessingMakernoteDirectory, null), // ORF_IMAGE_PROCESSING_TAGS 0x1113
            ExifInterface.TAG_ORF_PREVIEW_IMAGE_LENGTH to TagMapper(OlympusCameraSettingsMakernoteDirectory.TagPreviewImageLength, olympusCameraSettingsMakernoteDirectory, null), // ORF_CAMERA_SETTINGS_TAGS 0x0102
            ExifInterface.TAG_ORF_PREVIEW_IMAGE_START to TagMapper(OlympusCameraSettingsMakernoteDirectory.TagPreviewImageStart, olympusCameraSettingsMakernoteDirectory, null), // ORF_CAMERA_SETTINGS_TAGS 0x0101
            ExifInterface.TAG_ORF_THUMBNAIL_IMAGE to TagMapper(OlympusMakernoteDirectory.TAG_THUMBNAIL_IMAGE, olympusMakernoteDirectory, null), // ORF_MAKER_NOTE_TAGS 0x0100
            // RW2
            ExifInterface.TAG_RW2_ISO to TagMapper(PanasonicRawIFD0Directory.TagIso, panasonicRawIFD0Directory, null), // IFD_TIFF_TAGS 0x0017
            ExifInterface.TAG_RW2_JPG_FROM_RAW to TagMapper(PanasonicRawIFD0Directory.TagJpgFromRaw, panasonicRawIFD0Directory, null), // IFD_TIFF_TAGS 0x002E
            ExifInterface.TAG_RW2_SENSOR_BOTTOM_BORDER to TagMapper(PanasonicRawIFD0Directory.TagSensorBottomBorder, panasonicRawIFD0Directory, null), // IFD_TIFF_TAGS 0x0006
            ExifInterface.TAG_RW2_SENSOR_LEFT_BORDER to TagMapper(PanasonicRawIFD0Directory.TagSensorLeftBorder, panasonicRawIFD0Directory, null), // IFD_TIFF_TAGS 0x0005
            ExifInterface.TAG_RW2_SENSOR_RIGHT_BORDER to TagMapper(PanasonicRawIFD0Directory.TagSensorRightBorder, panasonicRawIFD0Directory, null), // IFD_TIFF_TAGS 0x0007
            ExifInterface.TAG_RW2_SENSOR_TOP_BORDER to TagMapper(PanasonicRawIFD0Directory.TagSensorTopBorder, panasonicRawIFD0Directory, null), // IFD_TIFF_TAGS 0x0004
    )

    // list of known ExifInterface tags (as of androidx.exifinterface:exifinterface:1.3.0)
    // mapped to metadata-extractor tags (as of v2.14.0)
    @JvmField
    val allTags: Map<String, TagMapper?> = hashMapOf<String, TagMapper?>(
    ).apply {
        putAll(baseTags)
        putAll(thumbnailTags)
        putAll(gpsTags)
        putAll(xmpTags)
        putAll(rawTags)
    }

    @JvmStatic
    fun describeAll(exif: ExifInterface): Map<String, Map<String, String>> {
        return HashMap<String, Map<String, String>>().apply {
            put("Exif", describeDir(exif, baseTags))
            put("Exif Thumbnail", describeDir(exif, thumbnailTags))
            put("GPS", describeDir(exif, gpsTags))
            put("XMP", describeDir(exif, xmpTags))
            put("Exif Raw", describeDir(exif, rawTags))
        }.filterValues { it.isNotEmpty() }
    }

    private fun describeDir(exif: ExifInterface, tags: Map<String, TagMapper?>): Map<String, String> {
        val dirMap = HashMap<String, String>()

        fillMetadataExtractorDir(exif, tags)

        for (kv in tags) {
            val exifInterfaceTag: String = kv.key
            if (exif.hasAttribute(exifInterfaceTag)) {
                val value: String? = exif.getAttribute(exifInterfaceTag)
                if (value != null && (value != "0" || !neverNullTags.contains(exifInterfaceTag))) {
                    val mapper = kv.value
                    if (mapper != null) {
                        val dir = mapper.dir
                        val type = mapper.type
                        val tagName = dir.getTagName(type)

                        val description: String? = dir.getDescription(type)
                        if (description != null) {
                            dirMap[tagName] = description
                        } else {
                            Log.w(LOG_TAG, "failed to get description for tag=$exifInterfaceTag value=$value")
                            dirMap[tagName] = value
                        }
                    } else {
                        dirMap[exifInterfaceTag] = value
                    }
                }
            }
        }
        return dirMap
    }

    private fun fillMetadataExtractorDir(exif: ExifInterface, tags: Map<String, TagMapper?>) {
        for (kv in tags) {
            val exifInterfaceTag: String = kv.key
            if (exif.hasAttribute(exifInterfaceTag)) {
                val value: String? = exif.getAttribute(exifInterfaceTag)
                if (value != null && (value != "0" || !neverNullTags.contains(exifInterfaceTag))) {
                    val mapper = kv.value
                    if (mapper != null) {
                        val obj: Any? = when (mapper.format) {
                            TagFormat.ASCII, TagFormat.COMMENT, TagFormat.UNDEFINED -> value
                            TagFormat.BYTE -> null // TODO TLAD convert ExifInterface string to byte
                            TagFormat.SHORT -> value.toShortOrNull()
                            TagFormat.LONG -> value.toLongOrNull()
                            TagFormat.RATIONAL -> toRational(value)
                            TagFormat.RATIONAL_ARRAY -> toRationalArray(value)
                            null -> null
                        }
                        if (obj != null) {
                            mapper.dir.setObject(mapper.type, obj)
                        }
                    }
                }
            }
        }
    }

    private fun toRational(s: String?): Rational? {
        s ?: return null

        // convert "12345/100"
        val parts = s.split("/")
        if (parts.size == 2) {
            val numerator = parts[0].toLongOrNull() ?: return null
            val denominator = parts[1].toLongOrNull() ?: return null
            return Rational(numerator, denominator)
        }

        // convert "123.45"
        var d = s.toDoubleOrNull() ?: return null
        if (d == 0.0) return Rational(0, 1)
        var denominator: Long = 1
        while (d != floor(d)) {
            denominator *= 10
            d *= 10
            if (denominator > 10000000000) {
                // let's not get irrational
                return null
            }
        }
        val numerator: Long = d.roundToLong()
        return Rational(numerator, denominator)
    }

    private fun toRationalArray(s: String?): Array<Rational>? {
        s ?: return null
        val list = s.split(",").mapNotNull { toRational(it) }
        if (list.isEmpty()) return null
        return list.toTypedArray()
    }
}

enum class TagFormat {
    ASCII, COMMENT, BYTE, SHORT, LONG, RATIONAL, RATIONAL_ARRAY, UNDEFINED
}

data class TagMapper(val type: Int, val dir: Directory, val format: TagFormat?)
