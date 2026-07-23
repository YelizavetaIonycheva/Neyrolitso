#libmbedtls
include $(app-root-dir)/submodules/externals/mbedtls/build/Android/Android.mk
#libbcunit
include $(app-root-dir)/submodules/bcunit/build/Android/Android.mk
#libbctoolbox
include $(app-root-dir)/submodules/bctoolbox/build/Android/Android-mbedtls.mk
#libbelr
include $(app-root-dir)/submodules/belr/build/Android/Android.mk
##libsqlite3 - using system sqlite3 via LOCAL_LDLIBS in submodules
#include $(app-root-dir)/submodules/externals/sqlite3/build/Android/Android.mk



##libortp
#include $(app-root-dir)/submodules/oRTP/build/Android/Android.mk
##libopus
##include $(app-root-dir)/submodules/externals/opus/build/Android/Android.mk
##libbcmatroska2
#include $(app-root-dir)/submodules/bcmatroska2/build/Android/Android.mk
##libsupport
#include $(app-root-dir)/submodules/support/build/Android/Android.mk
##libcpufeatures
#include $(app-root-dir)/submodules/cpufeatures/build/Android/Android.mk
##libturbojpeg
#include $(app-root-dir)/submodules/externals/libjpeg-turbo/build/Android/Android.mk
##libspeex
#include $(app-root-dir)/submodules/externals/speex/build/Android/Android.mk
##libbcg729
#include $(app-root-dir)/submodules/bcg729/build/Android/Android.mk
##libxml2
#include $(app-root-dir)/submodules/externals/libxml2/build/Android/Android.mk
##Video
##libvpx
#include $(app-root-dir)/submodules/externals/build/libvpx/Android/Android.mk
#
##libavutil
##libswresample
##libswscale
#include $(app-root-dir)/submodules/externals/build/ffmpeg/Android/Android.mk
##libopenh264
#include $(app-root-dir)/submodules/externals/build/openh264/Android/Android.mk
##libopencoreamr
#include $(app-root-dir)/submodules/externals/opencore-amr/build/Android/Android.mk
##libmsamr
#include $(app-root-dir)/submodules/msamr/build/Android/Android.mk
##libvoamrwbenc
#include $(app-root-dir)/submodules/externals/vo-amrwbenc/build/Android/Android.mk
##libmediastreamer2
#include $(app-root-dir)/submodules/mediastreamer2/build/Android/Android.mk
##libbelcard
#include $(app-root-dir)/submodules/belcard/build/Android/Android.mk
##libantlr3
#include $(app-root-dir)/submodules/externals/antlr3/build/Android/Android.mk
##libbellesip
#include $(app-root-dir)/submodules/belle-sip/build/Android/Android.mk
##liblinphone
#include $(app-root-dir)/submodules/linphone/build/Android/Android.mk


#libspmemvfs
include $(app-root-dir)/submodules/spmemvfs/Android.mk
#libspodb
include $(app-root-dir)/submodules/SpoDB/build/Android/Android.mk

#libhesh341112
include $(app-root-dir)/submodules/hesh341112/Android.mk

#libupdsch
include $(app-root-dir)/submodules/updsch/Android.mk
