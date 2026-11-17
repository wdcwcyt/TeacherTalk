package com.zl.teachertalk.bean

/**
 * 帖子编辑页中的一张图片草稿。
 */
sealed interface PostEditorImage {

    /**
     * 帖子原本已有、仍保留在帖子中的远程图片。
     *
     * @param url 服务端返回的图片相对地址
     */
    data class Existing(val url: String) : PostEditorImage

    /**
     * 用户新选择、尚未上传的本地图片。
     *
     * @param uriString Photo Picker 返回的本地 Uri 字符串
     */
    data class Local(val uriString: String) : PostEditorImage
}
