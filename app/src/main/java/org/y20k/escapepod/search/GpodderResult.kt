package org.y20k.escapepod.search

import com.google.gson.annotations.Expose

data class GpodderResult (@Expose val url: String,
                          @Expose val title: String,
                          @Expose val description: String,
                          @Expose val subscribers: Int,
                          @Expose val subscribers_last_week: Int,
                          @Expose val logo_url: String,
                          @Expose val scaled_logo_url: String,
                          @Expose val website: String,
                          @Expose val mygpo_link: String)