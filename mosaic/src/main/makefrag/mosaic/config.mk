# See LICENSE for license details.

##########################
# Project-specific default make variables
##########################

# These point at the main class of the target's Chisel generator
DESIGN_PACKAGE ?= firesim.mosaic
DESIGN ?= MoSAICWrapperHarness

# Re-uses the NoConfig from midasexamples for the default 'SimpleMoSAICHarness'
# which doesn't use any 'Parameters'
TARGET_CONFIG_PACKAGE ?= firesim.midasexamples
TARGET_CONFIG ?= NoConfig

# Must correlate with the 'PLATFORM' (part of the build N-tuplet / make
# argument). Defaults to the basic F1 config (since 'PLATFORM' should be 'f1')
PLATFORM_CONFIG_PACKAGE ?= midas
PLATFORM_CONFIG ?= XilinxAlveoU250Config
PLATFORM ?= xilinx_alveo_u250

# MoSAIC's legacy Verilog trips a handful of benign Verilator 5 lint checks
# (inferred latches in the qISA extension, an ascending range, a procedural
# assign to a declaration with an initializer, and similar declaration names).
# Waive them so the meta-simulator builds; they are all in the untouched
# MoSAIC-P38 sources.
EXTRA_VERILATOR_FLAGS ?= -Wno-LATCH -Wno-ASCRANGE -Wno-PROCASSINIT -Wno-SIMILARNAME
