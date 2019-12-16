package org.y20k.escapepod.search

data class GpodderResult (val url: String,
                          val title: String,
                          val description: String,
                          val subscribers: Int,
                          val subscribers_last_week: Int,
                          val logo_url: String,
                          val scaled_logo_url: String,
                          val website: String,
                          val mygpo_link: String)