# Define variables
BUILD_DIR = ./build
PRJ = mosaic

FIRESIM_ROOT ?= $(error FIRESIM_ROOT is not defined)
FIRESIM_MAIN_DIR = $(FIRESIM_ROOT)/sim/src/main
MOSAIC_MAIN_DIR = $(shell pwd)/mosaic/src/main

default: verilog

test:
	sbt ";project $(PRJ);test"

verilog:
	$(call git_commit, "generate verilog")
	mkdir -p $(BUILD_DIR)
	sbt ";project $(PRJ);runMain $(PRJ).Elaborate --target-dir $(BUILD_DIR)"

firrtl: verilog
	$(call git_commit, "generate firrtl")
	sbt ";project $(PRJ);runMain $(PRJ).Generator --target-dir $(BUILD_DIR) --top-module firesim.mosaic.MoSAICWrapperHarness --emit-legacy-sfc"

firesim:
	echo "[INFO] Installing MoSAIC FireSim into FireSim..."
	@for dir in $$(ls $(MOSAIC_MAIN_DIR)); do \
		echo "[INFO] Linking $(MOSAIC_MAIN_DIR)/$$dir/$(PRJ) to $(FIRESIM_MAIN_DIR)/$$dir/$(PRJ)"; \
		echo "    > ln -sf $(MOSAIC_MAIN_DIR)/$$dir/$(PRJ) $(FIRESIM_MAIN_DIR)/$$dir/."; \
		ln -sf $(MOSAIC_MAIN_DIR)/$$dir/$(PRJ) $(FIRESIM_MAIN_DIR)/$$dir/.; \
	done

help:
	sbt ";project $(PRJ);runMain $(PRJ).Elaborate --help"

reformat:
	mill -i __.reformat

checkformat:
	mill -i __.checkFormat

bsp:
	mill -i mill.bsp.BSP/install

idea:
	mill -i mill.idea.GenIdea/idea

clean:
	-rm -rf $(BUILD_DIR) ./out
	-rm -rf $(PRJ)/src/main/resources/vsrc
	@for dir in $$(ls $(MOSAIC_MAIN_DIR)); do \
		if [ -L $(FIRESIM_MAIN_DIR)/$$dir/$(PRJ) ]; then \
			echo "[INFO] Removing symlink $(FIRESIM_MAIN_DIR)/$$dir/$(PRJ)"; \
			echo "    > rm $(FIRESIM_MAIN_DIR)/$$dir/$(PRJ)"; \
			rm $(FIRESIM_MAIN_DIR)/$$dir/$(PRJ); \
		fi \
	done

.PHONY: test verilog help reformat checkformat clean

sim:
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	@echo "Write this Makefile by yourself."

-include ../Makefile
