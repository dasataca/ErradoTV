cmake_minimum_required(VERSION 3.22.1)
project("gareca_crypto")

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(gareca_crypto SHARED gareca_crypto.cpp)

find_library(log-lib log)

target_link_libraries(gareca_crypto ${log-lib})
