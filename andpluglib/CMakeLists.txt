cmake_minimum_required(VERSION 3.4.1)

set(TARGET andplug)

set(ROOT src/main/cpp)
set(BINIO ${ROOT}/libbinio/src)
set(ADPLUG ${ROOT}/adplug/src)

# oboe
set(OBOE_DIR ${ROOT}/oboe)
add_subdirectory(${OBOE_DIR} ./oboe)
include_directories(${OBOE_DIR}/include)

set(ROOT_FILES
        ${ROOT}/andplayer-jni.cpp
        ${ROOT}/andplayer-jni.h
        ${ROOT}/common.h
        ${ROOT}/AndPlug.cpp
        ${ROOT}/AndPlug.h
        ${ROOT}/OboePlayer.cpp
        ${ROOT}/OboePlayer.h
        ${ROOT}/Opl.cpp
        ${ROOT}/Opl.h
        )

# libbinio-1.5
set(BINIO_FILES
        ${BINIO}/binio.cpp
        ${BINIO}/binfile.cpp
        ${BINIO}/binwrap.cpp
        ${BINIO}/binstr.cpp
        )

# adplug-2.3.3
set(ADPLUG_FILES
        ${ADPLUG}/adlibemu.c
        ${ADPLUG}/debug.c
        ${ADPLUG}/fmopl.c
        ${ADPLUG}/nukedopl.c
        ${ADPLUG}/a2m.cpp
        ${ADPLUG}/adl.cpp
        ${ADPLUG}/adlib.cpp
        ${ADPLUG}/adplug.cpp
        ${ADPLUG}/adtrack.cpp
        ${ADPLUG}/amd.cpp
        ${ADPLUG}/analopl.cpp
        ${ADPLUG}/bam.cpp
        ${ADPLUG}/bmf.cpp
        ${ADPLUG}/cff.cpp
        ${ADPLUG}/cmf.cpp
        ${ADPLUG}/cmfmcsop.cpp
        ${ADPLUG}/d00.cpp
        ${ADPLUG}/database.cpp
        ${ADPLUG}/dfm.cpp
        ${ADPLUG}/diskopl.cpp
        ${ADPLUG}/dmo.cpp
        ${ADPLUG}/dro2.cpp
        ${ADPLUG}/dro.cpp
        ${ADPLUG}/dtm.cpp
        ${ADPLUG}/emuopl.cpp
        ${ADPLUG}/flash.cpp
        ${ADPLUG}/fmc.cpp
        ${ADPLUG}/fprovide.cpp
        ${ADPLUG}/got.cpp
        ${ADPLUG}/herad.cpp
        ${ADPLUG}/hsc.cpp
        ${ADPLUG}/hsp.cpp
        ${ADPLUG}/hybrid.cpp
        ${ADPLUG}/hyp.cpp
        ${ADPLUG}/imf.cpp
        ${ADPLUG}/jbm.cpp
        ${ADPLUG}/ksm.cpp
        ${ADPLUG}/lds.cpp
        ${ADPLUG}/mad.cpp
        ${ADPLUG}/mdi.cpp
        ${ADPLUG}/mid.cpp
        ${ADPLUG}/mkj.cpp
        ${ADPLUG}/msc.cpp
        ${ADPLUG}/mtk.cpp
        ${ADPLUG}/mus.cpp
        ${ADPLUG}/nemuopl.cpp
        ${ADPLUG}/player.cpp
        ${ADPLUG}/players.cpp
        ${ADPLUG}/protrack.cpp
        ${ADPLUG}/psi.cpp
        ${ADPLUG}/rad2.cpp
        ${ADPLUG}/rat.cpp
        ${ADPLUG}/raw.cpp
        ${ADPLUG}/realopl.cpp
        ${ADPLUG}/rix.cpp
        ${ADPLUG}/rol.cpp
        ${ADPLUG}/s3m.cpp
        ${ADPLUG}/sa2.cpp
        ${ADPLUG}/sng.cpp
        ${ADPLUG}/sop.cpp
        ${ADPLUG}/surroundopl.cpp
        ${ADPLUG}/temuopl.cpp
        ${ADPLUG}/u6m.cpp
        ${ADPLUG}/vgm.cpp
        ${ADPLUG}/woodyopl.cpp
        ${ADPLUG}/xad.cpp
        ${ADPLUG}/xsm.cpp
        ${ADPLUG}/../adplugdb/adplugdb.cpp
        )

add_library(${TARGET} SHARED ${ROOT_FILES} ${BINIO_FILES} ${ADPLUG_FILES})

find_library(LOG-LIB
             log)

include_directories(${ROOT} ${BINIO} ${ADPLUG})

set(FLAGS "-O2")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${FLAGS}")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${FLAGS}")
set(BINIO_FLAGS "PACKAGE_NAME=\"binio\";PACKAGE_TARNAME=\"libbinio\";PACKAGE_VERSION=\"1.5\";PACKAGE_STRING=\"binio 1.5\";PACKAGE_BUGREPORT=\"dn.tlp@gmx.net\";PACKAGE_URL=\"\";PACKAGE=\"libbinio\";VERSION=\"1.5\";STDC_HEADERS=1;HAVE_SYS_TYPES_H=1;HAVE_SYS_STAT_H=1;HAVE_STDLIB_H=1;HAVE_STRING_H=1;HAVE_MEMORY_H=1;HAVE_STRINGS_H=1;HAVE_INTTYPES_H=1;HAVE_STDINT_H=1;HAVE_UNISTD_H=1;HAVE_DLFCN_H=1")
set(ADPLUG_FLAGS "PACKAGE_NAME=\"adplug\";PACKAGE_TARNAME=\"adplug\";PACKAGE_VERSION=\"2.3.3\";PACKAGE_STRING=\"adplug 2.3.3\";PACKAGE_BUGREPORT=\"\";PACKAGE_URL=\"\";NDEBUG=/**/;PACKAGE=\"adplug\";VERSION=\"2.3.3\";STDC_HEADERS=1;HAVE_SYS_TYPES_H=1;HAVE_SYS_STAT_H=1;HAVE_STDLIB_H=1;HAVE_STRING_H=1;HAVE_MEMORY_H=1;HAVE_STRINGS_H=1;HAVE_INTTYPES_H=1;HAVE_STDINT_H=1;HAVE_UNISTD_H=1;HAVE_DLFCN_H=1;HAVE_GETOPT_H=1;stricmp=strcasecmp")
set_property(SOURCE ${BINIO_FILES} APPEND PROPERTY COMPILE_DEFINITIONS ${BINIO_FLAGS})
set_property(SOURCE ${ADPLUG_FILES} APPEND PROPERTY COMPILE_DEFINITIONS ${ADPLUG_FLAGS})

target_link_libraries(${TARGET}
                      ${LOG-LIB}
                      oboe)
