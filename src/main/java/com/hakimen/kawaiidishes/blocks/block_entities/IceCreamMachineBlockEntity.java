package com.hakimen.kawaiidishes.blocks.block_entities;

import com.hakimen.kawaiidishes.containers.IceCreamMachineContainer;
import com.hakimen.kawaiidishes.recipes.IceCreamMachineRecipe;
import com.hakimen.kawaiidishes.registry.BlockEntityRegister;
import com.hakimen.kawaiidishes.registry.ItemRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.Optional;

public class IceCreamMachineBlockEntity extends BlockEntity implements MenuProvider, BlockEntityTicker<IceCreamMachineBlockEntity> {

    public final ItemStackHandler inventory = createHandler();
    private final LazyOptional<IItemHandler> invHandler = LazyOptional.of(() -> inventory);
    public int progress = 0;
    public int recipeTicks = 0;

    protected final ContainerData data;

    private boolean isCrafting = false;

    public IceCreamMachineBlockEntity(BlockPos pWorldPosition, BlockState pBlockState) {
        super(BlockEntityRegister.iceCreamMachine.get(), pWorldPosition, pBlockState);
        this.data = new ContainerData() {
            public int get(int index) {
                switch (index) {
                    case 0:
                        return IceCreamMachineBlockEntity.this.progress;
                    case 1:
                        return IceCreamMachineBlockEntity.this.recipeTicks;
                    default:
                        return 0;
                }
            }

            public void set(int index, int value) {
                switch (index) {
                    case 0:
                        IceCreamMachineBlockEntity.this.progress = value;
                        break;
                    case 1:
                        IceCreamMachineBlockEntity.this.recipeTicks = value;
                        break;
                }
            }

            public int getCount() {
                return 2;
            }
        };
    }

    @Override
    protected void saveAdditional(CompoundTag pTag) {
        pTag.merge(this.inventory.serializeNBT());
        pTag.putInt("progress", progress);
        pTag.putInt("recipeTicks", recipeTicks);
        pTag.putBoolean("isCrafting", isCrafting);
        super.saveAdditional(pTag);
    }

    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        progress = pTag.getInt("progress");
        recipeTicks = pTag.getInt("recipeTicks");
        isCrafting = pTag.getBoolean("isCrafting");
        this.inventory.deserializeNBT(pTag);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this, BlockEntity::saveWithFullMetadata);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithFullMetadata();
    }

    public static boolean hasRecipe(IceCreamMachineBlockEntity entity) {
        Level level = entity.level;
        SimpleContainer inventory = new SimpleContainer(entity.inventory.getSlots());
        for (int i = 0; i < entity.inventory.getSlots() - 1; i++) {
            if (entity.inventory.getStackInSlot(i) != ItemStack.EMPTY) {
                inventory.setItem(i, entity.inventory.getStackInSlot(i));
            }
        }
        Optional<IceCreamMachineRecipe> match = level.getRecipeManager()
                .getRecipeFor(IceCreamMachineRecipe.Type.INSTANCE, inventory, level);
        return match.isPresent();
    }


    @Override
    public void tick(Level pLevel, BlockPos pPos, BlockState pState, IceCreamMachineBlockEntity pBlockEntity) {

        if (hasRecipe(pBlockEntity)) {
            Level level = pBlockEntity.level;
            SimpleContainer inventory = new SimpleContainer(pBlockEntity.inventory.getSlots());
            for (int i = 0; i < pBlockEntity.inventory.getSlots() - 1; i++) {
                if (pBlockEntity.inventory.getStackInSlot(i) != ItemStack.EMPTY) {
                    inventory.setItem(i, pBlockEntity.inventory.getStackInSlot(i));
                }
            }
            Optional<IceCreamMachineRecipe> match = level.getRecipeManager()
                    .getRecipeFor(IceCreamMachineRecipe.Type.INSTANCE, inventory, level);
            if (match.isPresent()) {
                IceCreamMachineRecipe recipe = match.get();
                if (!isCrafting) {
                    isCrafting = true;
                    recipeTicks = recipe.getTicks();
                    setChanged();
                } else {
                    this.progress++;


                    setChanged();
                    if (progress >= recipeTicks) {
                        isCrafting = false;
                        progress = 0;
                        for (int i = 0; i < pBlockEntity.inventory.getSlots() - 1; i++) {
                            if (i == 1 || i == 2 || i == 3) {
                                var stack = pBlockEntity.inventory.getStackInSlot(i).getCraftingRemainingItem();
                                pBlockEntity.inventory.extractItem(i,1,false);
                                if (!stack.equals(ItemStack.EMPTY)) {
                                    pBlockEntity.inventory.setStackInSlot(i, stack);
                                }
                            } else {
                                pBlockEntity.inventory.getStackInSlot(i).shrink(1);
                            }

                        }
                        pBlockEntity.inventory.setStackInSlot(4, recipe.getResultItem());
                        setChanged();
                    }
                }
            }
        } else if (progress > 0) {
            progress--;
        }
        setChanged();

    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return (LazyOptional<T>) invHandler;
        } else {
            return super.getCapability(cap,side);
        }
    }

    private ItemStackHandler createHandler() {
        return new ItemStackHandler(6) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                switch (slot) {
                    case 0 -> {
                        return stack.getItem().equals(Items.SNOWBALL);
                    }
                    case 1, 2, 3 -> {
                        return true;
                    }
                    case 4 -> {
                        return stack.getItem().equals(ItemRegister.glassCup.get());
                    }
                }
                return false;
            }


            @Override
            public int getSlotLimit(int slot) {
                return slot == 4 ? 1 : 64;
            }

            @Nonnull
            @Override
            public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                if (!isItemValid(slot, stack)) {
                    return stack;
                }

                return super.insertItem(slot, stack, simulate);
            }
        };
    }


    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.kawaiidishes.ice_cream_machine");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int pContainerId, Inventory pInventory, Player pPlayer) {
        return new IceCreamMachineContainer(pContainerId, pInventory, this, this.data);
    }
}
