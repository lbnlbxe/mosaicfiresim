# See LICENSE for license details.

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
ifneq ($(MOSAIC_ELAB),chisel6)
ifneq ($(MOSAIC_ELAB),native)
$(error MOSAIC_ELAB must be 'chisel6' or 'native', got '$(MOSAIC_ELAB)')
endif
endif

MOSAIC_REPO_ROOT := $(abspath $(firesim_base_dir)/../../..)
MOSAIC_STAGE_DIR := $(GENERATED_DIR)/chisel6-stage
# Records which elaboration path produced the current artifacts; switching
# MOSAIC_ELAB invalidates them (checked at parse time below, since a mode
# change otherwise leaves stale FIRRTL newer than any source prerequisite).
MOSAIC_ELAB_STAMP := $(GENERATED_DIR)/.mosaic-elab-mode
ifneq ($(shell cat $(MOSAIC_ELAB_STAMP) 2>/dev/null),$(MOSAIC_ELAB))
$(shell rm -f $(FIRRTL_FILE) $(ANNO_FILE); rm -rf $(MOSAIC_STAGE_DIR))
endif

# The Chisel 6 stage regenerates 'mosaic.preprocessed.sv' (into
# src/main/resources/vsrc) and MoSAIC-P38 scratch outputs (build/, and
# untracked icarus/file_list.txt) as side effects of elaboration; tracking
# those as prerequisites would make the FIRRTL target perpetually out of
# date. Only real sources trigger re-elaboration.
MOSAIC_STAGE_SRCS := \
	$(shell find -L $(MOSAIC_REPO_ROOT)/build.sbt \
	               $(MOSAIC_REPO_ROOT)/mosaic/src/main/scala \
	               $(MOSAIC_REPO_ROOT)/mosaic/src/main/scala-chisel6 \
	               $(MOSAIC_REPO_ROOT)/mosaic/src/main/resources/mosaic/perl \
	               $(MOSAIC_REPO_ROOT)/mosaic/src/main/resources/mosaic/MoSAIC-P38/src \
	               -type f 2>/dev/null)

ifeq ($(MOSAIC_ELAB),chisel6)
# Requires JDK 17 (Chisel 6) and 'sbt' on PATH; the Chisel 6 stage runs the
# MoSAIC perl preprocessing itself (see mosaic/src/main/scala/mosaic/mosaic.scala).
$(FIRRTL_FILE) $(ANNO_FILE) &: $(MOSAIC_STAGE_SRCS)
	@mkdir -p $(MOSAIC_STAGE_DIR) $(@D)
	cd $(MOSAIC_REPO_ROOT) && sbt ";project mosaic; runMain mosaic.Generator --target-dir $(MOSAIC_STAGE_DIR) --top-module $(DESIGN_PACKAGE).$(DESIGN) --emit-legacy-sfc"
	cp $(MOSAIC_STAGE_DIR)/$(DESIGN_PACKAGE).sfc.fir $(FIRRTL_FILE)
	cp $(MOSAIC_STAGE_DIR)/$(DESIGN_PACKAGE).anno.json $(ANNO_FILE)
	printf '%s\n' '$(MOSAIC_ELAB)' > $(MOSAIC_ELAB_STAMP)
else
$(FIRRTL_FILE) $(ANNO_FILE) &: $(TARGET_CP)
	@mkdir -p $(@D)
	$(call run_jar_scala_main,$(firesim_base_dir),$(TARGET_CP),midas.chiselstage.Generator,\
		--target-dir $(GENERATED_DIR) \
		--name $(long_name) \
		--top-module $(DESIGN_PACKAGE).$(DESIGN) \
		--configs $(TARGET_CONFIG_QUALIFIED))
	printf '%s\n' '$(MOSAIC_ELAB)' > $(MOSAIC_ELAB_STAMP)
endif
