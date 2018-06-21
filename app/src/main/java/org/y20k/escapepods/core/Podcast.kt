/*
 * Podcast.kt
 * Implements the Podcast class
 * A Podcast object holds the base data of a podcast and a list of its Episodes
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.core

import android.net.Uri
import android.support.v4.media.MediaBrowserCompat
import java.util.*


/*
 * Podcast class
 */
class Podcast(var title : String,
              var website : Uri,
              var episodes: TreeMap<String, MediaBrowserCompat.MediaItem>) {

}