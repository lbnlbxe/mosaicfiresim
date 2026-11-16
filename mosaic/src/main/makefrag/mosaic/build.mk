# See LICENSE for license details.

##########################
# Target-RTL generation:
#
# Generate the 'FIRRTL_FILE' and 'ANNO_FILE' consumed by the Golden Gate
# (midas) compiler. There is exactly one elaboration path:
#
#   MoSAIC's own Chisel 6 stage elaborates the harness under Chisel 6 and
#   emits LEGACY FIRRTL ('--emit-legacy-sfc') plus firrtl-1.x-compatible
#   annotations. This is the intended cross-version hand-off: the Chisel 6
#   build is the source of truth, and FireSim's Chisel 3.6 Golden Gate
#   consumes the emitted files without re-elaborating the target. The
#   Chisel 6 / FireSim 3.6 version boundary is enforced by construction:
#   nothing in this flow elaborates the target under FireSim's toolchain.
##########################

MOSAIC_REPO_ROOT := $(abspath $(firesim_base_dir)/../../..)
MOSAIC_STAGE_DIR := $(GENERATED_DIR)/chisel6-stage

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

# Requires JDK 17 (Chisel 6) and 'sbt' on PATH; the Chisel 6 stage runs the
# MoSAIC perl preprocessing itself (see mosaic/src/main/scala/mosaic/mosaic.scala).
# Guard: the find above swallows errors (2>/dev/null), so a miscomputed
# MOSAIC_REPO_ROOT would silently yield empty prerequisites and the build
# would run against stale FIRRTL/annos without ever re-elaborating.
ifeq ($(MOSAIC_STAGE_SRCS),)
$(error MOSAIC_STAGE_SRCS is empty - MOSAIC_REPO_ROOT '$(MOSAIC_REPO_ROOT)' does not contain the MoSAIC target sources (build.sbt, mosaic/src/main/...))
endif

$(FIRRTL_FILE) $(ANNO_FILE) &: $(MOSAIC_STAGE_SRCS)
	@mkdir -p $(MOSAIC_STAGE_DIR) $(@D)
	cd $(MOSAIC_REPO_ROOT) && sbt ";project mosaic; runMain mosaic.Generator --target-dir $(MOSAIC_STAGE_DIR) --top-module $(DESIGN_PACKAGE).$(DESIGN) --emit-legacy-sfc"
	cp $(MOSAIC_STAGE_DIR)/$(DESIGN_PACKAGE).sfc.fir $(FIRRTL_FILE)
	cp $(MOSAIC_STAGE_DIR)/$(DESIGN_PACKAGE).anno.json $(ANNO_FILE)
