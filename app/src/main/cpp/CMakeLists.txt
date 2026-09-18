cmake_minimum_required(VERSION 3.22.1)
project(llm-personalization CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---- llama.cpp build flags ----
# Thin build. No tests, no examples, no server, no HTTP client.
# CPU only (Vulkan/Metal/CUDA off) - the shipping target is Pixel 10 CPU.
set(BUILD_SHARED_LIBS       OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS       OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS       OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON      OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL              OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP             OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE          OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN             OFF CACHE BOOL "" FORCE)
set(GGML_CUDA               OFF CACHE BOOL "" FORCE)
set(GGML_METAL              OFF CACHE BOOL "" FORCE)
set(GGML_ACCELERATE         OFF CACHE BOOL "" FORCE)

# llama.cpp sits as a git submodule next to this CMakeLists.txt.
add_subdirectory(llama.cpp EXCLUDE_FROM_ALL)

# ---- JNI bridge ----
add_library(jni_bridge SHARED
    jni_bridge.cpp
)

target_include_directories(jni_bridge PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(jni_bridge
    llama
    android
    log
)
# ---- 16 KB page size alignment (Android 15+) ----
target_link_options(jni_bridge PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
