cmake_minimum_required(VERSION 3.22)
project(pocketdictate LANGUAGES C CXX)

# transcribe.cpp is pinned as a git submodule at the same version Handy's
# latest release uses (v0.2.4, commit 4807edaf210d0d7e8a6f7fb2a44b65966a2797f0).
set(TRANSCRIBE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/transcribe.cpp)
get_filename_component(TRANSCRIBE_DIR ${TRANSCRIBE_DIR} ABSOLUTE)

# --- transcribe.cpp options: static library, CPU backend only --------------
set(TRANSCRIBE_BUILD_TESTS       OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_BUILD_EXAMPLES    OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_BUILD_TOOLS       OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_BUILD_SHARED      OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_GGML_BACKEND_DL   OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_METAL             OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_VULKAN            OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_CUDA              OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_HIP               OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_USE_OPENMP        OFF CACHE BOOL "" FORCE)
# No system BLAS on Android; transcribe.cpp falls back to its own
# multithreaded loops (the same posture as its official Python wheels).
set(TRANSCRIBE_USE_SYSTEM_BLAS   OFF CACHE BOOL "" FORCE)

# --- ARM optimisations -------------------------------------------------------
# Snapdragon 8 Elite for Galaxy (S25 series) supports ARMv8.2 dot-product,
# half-precision and int8 matrix-multiply (i8mm) instructions, which ggml
# uses for fast Q8_0 maths. GGML_NATIVE must be off when cross-compiling.
set(GGML_NATIVE       OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16+i8mm" CACHE STRING "" FORCE)

# Static archives are linked into our shared JNI library, so they must be PIC.
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

add_subdirectory(${TRANSCRIBE_DIR} transcribe EXCLUDE_FROM_ALL)

# transcribe.cpp's src/CMakeLists.txt points its public include path at
# ${CMAKE_SOURCE_DIR}/include, which assumes it is the top-level project.
# Add the correct path here instead of modifying the pinned submodule.
target_include_directories(transcribe PUBLIC ${TRANSCRIBE_DIR}/include)

# --- Our JNI bridge ----------------------------------------------------------
add_library(pocketdictate_jni SHARED pocket_dictate_jni.cpp)
target_include_directories(pocketdictate_jni PRIVATE ${TRANSCRIBE_DIR}/include)
target_link_libraries(pocketdictate_jni PRIVATE transcribe log)
# Android 15+ devices may use 16 KB memory pages.
target_link_options(pocketdictate_jni PRIVATE "-Wl,-z,max-page-size=16384")
