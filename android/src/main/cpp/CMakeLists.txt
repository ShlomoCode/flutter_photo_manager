cmake_minimum_required(VERSION 3.10)
project(photo_manager_file_times LANGUAGES C)

add_library(photo_manager_file_times SHARED cache_file_times.c)
target_compile_features(photo_manager_file_times PRIVATE c_std_11)
target_compile_options(photo_manager_file_times PRIVATE -Wall -Wextra -Werror)
