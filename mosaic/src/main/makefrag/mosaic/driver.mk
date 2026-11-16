# See LICENSE for license details.

##########################
# Driver Sources & Flags:
#
# used to point midas compiler to specific *.h and *.cc files, as well as
# corresponding CXX/LD flags.
##########################

DRIVER_H := $(firesim_base_dir)/src/main/cc/mosaic/bridges/axilite.h
# Simulation top selection:
#   mosaic_top          — programs MoSAIC via the AxiLiteBridge (default)
#   simple_mosaic_top   — reset-only smoke test, no AxiLiteBridge
MOSAIC_SIM_TOP ?= mosaic_top
DRIVER_CC := $(firesim_base_dir)/src/main/cc/mosaic/$(MOSAIC_SIM_TOP).cc \
             $(firesim_base_dir)/src/main/cc/mosaic/bridges/axilite.cc
# -I src/main/cc/mosaic lets the generated header's `#include "bridges/axilite.h"`
# resolve to the target-local bridge driver (before FireSim's own bridges dir).
TARGET_CXX_FLAGS := -g -I$(firesim_base_dir)/src/main/cc/mosaic \
	-DMOSAIC_FW_DIR=$(abspath $(firesim_base_dir)/src/main/resources/mosaic/MoSAIC-P38/src/Tile.HDL/picorv32_tile/firmware)
TARGET_LD_FLAGS :=
