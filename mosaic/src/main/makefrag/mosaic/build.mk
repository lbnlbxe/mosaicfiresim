# See LICENSE for details.

##########################
# Target-RTL generation:
#
# Generate the 'FIRRTL_FILE' and 'ANNO_FILE' consumed by the Golden Gate
# (midas) compiler. Two elaboration paths are available (MOSAIC_ELAB):
#
#   chisel6 (default): MoSAIC's own Chisel 6 stage elaborates the harness
#     under Chisel 6 and emits LEGACY FIRRTL ('--emit-legacy-sfc') plus
#     firrtl-1.x-compatible annotations. This is the intended cross-version
#     hand-off: the Chisel 6 build is the source of truth, and FireSim's
#     Chisel 3.6 Golden Gate consumes the emitted files without
#     re-elaborating the target.
#
#   native: legacy in-FireSim elaboration via 'midas.chiselstage.Generator'
#     under FireSim's Chisel 3.6. Kept as a debugging fallback; the two paths
#     elaborate the same shared scala/ tree.
##########################

MOSAIC_ELAB ?= chisel6

MOSAIC_REPO_ROOT := $(abspath $(firesim_base_dir)/../../..)
MOSAIC_STAGE_DIR := $(GENERATED_DIR)/chisel6-stage
# The Chisel 6 stage regenerates 'mosaic.preprocessed.sv' (into resources/vsrc)
# and the perl scratch dirs (MoSAIC-P38 build/icarus/generate) as side effects
# of elaboration; tracking those as prerequisites would make the FIRRTL target
# perpetually out of date. Only real sources trigger re-elaboration.
MOSAIC_STAGE_SRCS := \
	$(shell find -L $(MOSAIC_REPO_ROOT)/mosaic/src/main/scala \
	               $(MOSAIC_REPO_ROOT)/mosaic/src/main/resources/mosaic/perl \
	               $(MOSAIC_REPO_ROOT)/mosaic/src/main/resources/mosaic/MoSAIC-P38/src \
	               -type f 2>/dev/null)

ifeq ($(MOSAIC_ELAB),chisel6)
# Requires JDK 17 (Chisel 6) and 'sbt' on PATH; the Chisel 6 stage runs the
# MoSAIC perl preprocessing itself (see mosaic/src/main/scala/mosaic/mosaic.scala).
$(FIRRTL_FILE) $(ANNO_FILE) &: $(MOSAIC_STAGE_SRCS)
	@mkdir -p $(MOSAIC_STAGE_DIR)
	cd $(MOSAIC_REPO_ROOT) && sbt ";project mosaic; runMain mosaic.Generator \
		--target-dir $(MOSAIC_STAGE_DIR) \
		--top-module $(DESIGN_PACKAGE).$(DESIGN) \
		--emit-legacy-sfc"
	@mkdir -p $(@D)
	cp $(MOSAIC_STAGE_DIR)/$(DESIGN_PACKAGE).sfc.fir $(FIRRTL_FILE)
	cp $(MOSAIC_STAGE_DIR)/$(DESIGN_PACKAGE).anno.json $(ANNO_FILE)
else
$(FIRRTL_FILE) $(ANNO_FILE) &: $(TARGET_CP)
	@mkdir -p $(@D)
	$(call run_jar_scala_main,$(firesim_base_dir),$(TARGET_CP),midas.chiselstage.Generator,\
		--target-dir $(GENERATED_DIR) \
		--name $(long_name) \
		--top-module $(DESIGN_PACKAGE).$(DESIGN) \
		--configs $(TARGET_CONFIG_QUALIFIED))
endif
