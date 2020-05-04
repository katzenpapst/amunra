package de.katzenpapst.amunra.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import de.katzenpapst.amunra.AmunRa;
import de.katzenpapst.amunra.crafting.RecipeHelper;
import de.katzenpapst.amunra.helper.AstronomyHelper;
import de.katzenpapst.amunra.helper.ShuttleTeleportHelper;
import de.katzenpapst.amunra.mothership.Mothership;
import de.katzenpapst.amunra.network.packet.PacketSimpleAR;
import de.katzenpapst.amunra.vec.BoxInt2D;
import micdoodle8.mods.galacticraft.api.galaxies.CelestialBody;
import micdoodle8.mods.galacticraft.api.galaxies.IChildBody;
import micdoodle8.mods.galacticraft.api.galaxies.Satellite;
import micdoodle8.mods.galacticraft.api.recipe.SpaceStationRecipe;
import micdoodle8.mods.galacticraft.core.client.gui.screen.GuiCelestialSelection;
import micdoodle8.mods.galacticraft.core.util.ColorUtil;
import micdoodle8.mods.galacticraft.core.util.GCCoreUtil;
import micdoodle8.mods.galacticraft.core.util.GCLog;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;

public class GuiShuttleSelection extends GuiARCelestialSelection {

    protected boolean createMothershipButtonDisabled = false;

    protected BoxInt2D exitBtnArea = new BoxInt2D();
    protected BoxInt2D buildMsBtnArea = new BoxInt2D();

    public GuiShuttleSelection(boolean mapMode, List<CelestialBody> possibleBodies)
    {
        super(mapMode, possibleBodies);
    }

    protected CelestialBody getParent(CelestialBody body) {
        if(body instanceof IChildBody) {// satellite apparently implements this already?
            return ((IChildBody)body).getParentPlanet();
        }
        if(body instanceof Mothership) {
            return ((Mothership)body).getParent();
        }
        return body;
    }

    @Override
    public void initGui() {
        super.initGui();

        CelestialBody currentPlayerBody = ShuttleTeleportHelper.getCelestialBodyForDimensionID(this.mc.thePlayer.dimension);
        if(currentPlayerBody != null) {
            selectAndZoom(currentPlayerBody);
        }
    }



    @Override
    public void drawButtons(int mousePosX, int mousePosY)
    {
        this.possibleBodies = this.shuttlePossibleBodies;
        super.drawButtons(mousePosX, mousePosY);
        if (this.selectionState != EnumSelectionState.PROFILE && this.selectedBody != null && canCreateMothership(this.selectedBody))
        {
            drawMothershipButton(mousePosX, mousePosY);
        }

        // exit button

        GL11.glColor4f(0.0F, 1.0F, 0.1F, 1);
        this.mc.renderEngine.bindTexture(GuiCelestialSelection.guiMain0);


        int exitWidth = width - GuiCelestialSelection.BORDER_WIDTH - GuiCelestialSelection.BORDER_EDGE_WIDTH - 74;
        int exitHeight = height - GuiCelestialSelection.BORDER_WIDTH - GuiCelestialSelection.BORDER_EDGE_WIDTH - 11;

        exitBtnArea.setPositionSize(exitWidth, exitHeight, 74, 11);

        this.drawTexturedModalRect(
                exitBtnArea.minX,
                exitBtnArea.minY,
                exitBtnArea.getWidth(),
                exitBtnArea.getHeight(),
                0, 392, 148, 22, true, true);
        String str = GCCoreUtil.translate("gui.message.cancel.name").toUpperCase();
        this.fontRendererObj.drawString(str,
                exitBtnArea.minX + (exitBtnArea.getWidth()-this.fontRendererObj.getStringWidth(str))/2,
                exitBtnArea.minY + 2, ColorUtil.to32BitColor(255, 255, 255, 255));
    }

    @Override
    protected void keyTyped(char keyChar, int keyID)
    {
        super.keyTyped(keyChar, keyID);

    }

    @Override
    protected boolean canCreateSpaceStation(CelestialBody atBody) {
        // no stations can be built from the shuttle, because there's not enough space on the screen
        return false;
    }

    protected boolean canCreateMothership(CelestialBody atBody) {
        if(numPlayersMotherships < 0) {
            return false;
        }
        // important! check where the player started from
        if(playerParent == null) {
            return false;
        }

        return (
                AmunRa.config.maxNumMotherships == -1 ||
                numPlayersMotherships < AmunRa.config.maxNumMotherships
                ) && playerParent == selectedBody && Mothership.canBeOrbited(atBody);
    }

    protected void drawItemForRecipe(ItemStack item, int amount, int requiredAmount, int xPos, int yPos, int mousePosX, int mousePosY)
    {
        RenderHelper.enableGUIStandardItemLighting();
        GuiCelestialSelection.itemRender.renderItemAndEffectIntoGUI(
                this.fontRendererObj,
                this.mc.renderEngine,
                item, xPos, yPos);
        RenderHelper.disableStandardItemLighting();
        GL11.glEnable(GL11.GL_BLEND);

        if(isMouseWithin(mousePosX, mousePosY, xPos, yPos, 16, 16))
        {
            this.showTooltip(item.getDisplayName(), mousePosX, mousePosY);
        }

        String str = "" + amount + "/" + requiredAmount;
        boolean valid = amount >= requiredAmount;

        int color = valid | this.mc.thePlayer.capabilities.isCreativeMode ? ColorUtil.to32BitColor(255, 0, 255, 0) : ColorUtil.to32BitColor(255, 255, 0, 0);
        this.smallFontRenderer.drawString(
                str,
                xPos + 8 - this.smallFontRenderer.getStringWidth(str) / 2,
                //offset + GuiCelestialSelection.BORDER_WIDTH + GuiCelestialSelection.BORDER_EDGE_WIDTH + 154 + canCreateOffset
                yPos+16, color);


        /* posY = c+154;
         * drawStr=c+170
         * c = posY-154
         * drawStr = posY-154+170
         * drawStr = posY+16
         */

    }
/* TODO find a way to do this
    @Override
    protected int getAmountInInventory(ItemStack stack)
    {
        int amountInInv = super.getAmountInInventory(stack);

        EntityClientPlayerMP player = FMLClientHandler.instance().getClientPlayerEntity();

        Entity rocket = player.ridingEntity;

        //GCPlayerStats

        if(rocket instanceof EntityAutoRocket) {
            EntityAutoRocket realRocket = (EntityAutoRocket)rocket;

            for (int x = 0; x < realRocket.getSizeInventory(); x++)
            {
                final ItemStack slot = realRocket.getStackInSlot(x);

                if (slot != null)
                {
                    if (SpaceStationRecipe.checkItemEquals(stack, slot))
                    {
                        amountInInv += slot.stackSize;
                    }
                }
            }

        }

        // now also try to check the ship's inventory


        return amountInInv;
    }
*/
    protected void drawMothershipButton(int mousePosX, int mousePosY)
    {
        int offset=0;

        GL11.glColor4f(0.0F, 0.6F, 1.0F, 1);
        this.mc.renderEngine.bindTexture(GuiCelestialSelection.guiMain1);
        int canCreateLength = Math.max(0, this.drawSplitString(GCCoreUtil.translate("gui.message.canCreateMothership.name"), 0, 0, 91, 0, true, true) - 2);
        int canCreateOffset = canCreateLength * this.smallFontRenderer.FONT_HEIGHT;

        /*x > width - GuiCelestialSelection.BORDER_WIDTH - GuiCelestialSelection.BORDER_EDGE_WIDTH - 96 &&
        x < width - GuiCelestialSelection.BORDER_WIDTH - GuiCelestialSelection.BORDER_EDGE_WIDTH &&
        y > GuiCelestialSelection.BORDER_WIDTH + GuiCelestialSelection.BORDER_EDGE_WIDTH + 182 &&
        y < GuiCelestialSelection.BORDER_WIDTH + GuiCelestialSelection.BORDER_EDGE_WIDTH + 182 + 12*/

        this.drawTexturedModalRect(
                width - GuiCelestialSelection.BORDER_WIDTH - GuiCelestialSelection.BORDER_EDGE_WIDTH - 95, // x
                offset+GuiCelestialSelection.BORDER_WIDTH + GuiCelestialSelection.BORDER_EDGE_WIDTH + 134,        // y
                93, //w
                4,  //h
                159, // u
                102, //v
                93, //uWidth
                4,  //uHeight
                false, false);
        for (int barY = 0; barY < canCreateLength; ++barY)
        {
            this.drawTexturedModalRect(
                    width - GuiCelestialSelection.BORDER_WIDTH - GuiCelestialSelection.BORDER_EDGE_WIDTH - 95,
                    offset+GuiCelestialSelection.BORDER_WIDTH + GuiCelestialSelection.BORDER_EDGE_WIDTH + 138 + barY * this.smallFontRenderer.FONT_HEIGHT,
                    93,
                    this.smallFontRenderer.FONT_HEIGHT, 159, 106, 93, this.smallFontRenderer.FONT_HEIGHT, false, false);
        }
        this.drawTexturedModalRect(width - GuiCelestialSelection.BORDER_WIDTH - GuiCelestialSelection.BORDER_EDGE_WIDTH - 95, offset + GuiCelestialSelection.BORDER_WIDTH + GuiCelestialSelection.BORDER_EDGE_WIDTH + 138 + canCreateOffset, 93, 43, 159, 106, 93, 43, false, false);
        this.drawTexturedModalRect(width - GuiCelestialSelection.BORDER_WIDTH - GuiCelestialSelection.BORDER_EDGE_WIDTH - 79, offset + GuiCelestialSelection.BORDER_WIDTH + GuiCelestialSelection.BORDER_EDGE_WIDTH + 129, 61, 4, 0, 170, 61, 4, false, false);

        int xPos = 0;
        int yPos = offset + GuiCelestialSelection.BORDER_WIDTH + GuiCelestialSelection.BORDER_EDGE_WIDTH + 154 + canCreateOffset;
        //
        SpaceStationRecipe recipe = RecipeHelper.mothershipRecipe;
        if (recipe != null)
        {
            GL11.glColor4f(0.0F, 1.0F, 0.1F, 1);
            boolean validInputMaterials = true;

            int i = 0;
            for (Map.Entry<Object, Integer> e : recipe.getInput().entrySet())
            {
                Object next = e.getKey();
                xPos = (int)(width - GuiCelestialSelection.BORDER_WIDTH - GuiCelestialSelection.BORDER_EDGE_WIDTH - 95 + i * 93 / (double)recipe.getInput().size() + 5);
                // int yPos = GuiCelestialSelection.BORDER_WIDTH + GuiCelestialSelection.BORDER_EDGE_WIDTH + 154 + canCreateOffset;
                int requiredAmount = e.getValue();

                if (next instanceof ItemStack)
                {
                    int amount = getAmountInInventory((ItemStack) next);
                    drawItemForRecipe(((ItemStack) next).copy(), amount, requiredAmount, xPos, yPos, mousePosX, mousePosY);
                    validInputMaterials = (amount >= requiredAmount && validInputMaterials);

                } // if itemstack
                else if (next instanceof ArrayList)
                {
                    ArrayList<ItemStack> items = (ArrayList<ItemStack>) next;

                    int amount = 0;

                    for (ItemStack stack : items)
                    {
                        amount += getAmountInInventory(stack);
                    }
                    ItemStack stack = items.get((this.ticksSinceMenuOpen / 20) % items.size()).copy();
                    drawItemForRecipe(stack, amount, requiredAmount, xPos, yPos, mousePosX, mousePosY);
                    validInputMaterials = (amount >= requiredAmount && validInputMaterials);
                }

                i++;
            }

            if ((validInputMaterials || this.mc.thePlayer.capabilities.isCreativeMode) && !createMothershipButtonDisabled)
            {
                GL11.glColor4f(0.0F, 1.0F, 0.1F, 1);
            }
            else
            {
                GL11.glColor4f(1.0F, 0.0F, 0.0F, 1);
            }

            this.mc.renderEngine.bindTexture(GuiCelestialSelection.guiMain1);


            buildMsBtnArea.setPositionSize(
                    width - GuiCelestialSelection.BORDER_WIDTH - GuiCelestialSelection.BORDER_EDGE_WIDTH - 95,
                    offset + GuiCelestialSelection.BORDER_WIDTH + GuiCelestialSelection.BORDER_EDGE_WIDTH + 182 + canCreateOffset,
                    93, 12);

            if (!this.mapMode)
            {
                if (buildMsBtnArea.isWithin(mousePosX, mousePosY))
                {
                    this.drawTexturedModalRect(
                            buildMsBtnArea.minX,
                            buildMsBtnArea.minY,
                            buildMsBtnArea.getWidth(), buildMsBtnArea.getHeight(),
                            0, 174, 93, 12, false, false);
                }
            }

            this.drawTexturedModalRect(
                    buildMsBtnArea.minX,
                    buildMsBtnArea.minY,
                    buildMsBtnArea.getWidth(), buildMsBtnArea.getHeight(),
                    0, 174, 93, 12, false, false);

            int color = (int)((Math.sin(this.ticksSinceMenuOpen / 5.0) * 0.5 + 0.5) * 255);
            this.drawSplitString(
                    GCCoreUtil.translate("gui.message.canCreateMothership.name"),
                    width - GuiCelestialSelection.BORDER_WIDTH - GuiCelestialSelection.BORDER_EDGE_WIDTH - 48,
                    offset + GuiCelestialSelection.BORDER_WIDTH + GuiCelestialSelection.BORDER_EDGE_WIDTH + 137, 91, ColorUtil.to32BitColor(255, color, 255, color), true, false);

            if (!mapMode)
            {
                this.drawSplitString(
                        GCCoreUtil.translate("gui.message.createSS.name").toUpperCase(),
                        width - GuiCelestialSelection.BORDER_WIDTH - GuiCelestialSelection.BORDER_EDGE_WIDTH - 48, offset + GuiCelestialSelection.BORDER_WIDTH + GuiCelestialSelection.BORDER_EDGE_WIDTH + 185 + canCreateOffset, 91, ColorUtil.to32BitColor(255, 255, 255, 255), false, false);
            }
        } // if (recipe != null)
        else
        {
            this.drawSplitString(
                    GCCoreUtil.translate("gui.message.cannotCreateSpaceStation.name"),
                    width - GuiCelestialSelection.BORDER_WIDTH - GuiCelestialSelection.BORDER_EDGE_WIDTH - 48,
                    offset + GuiCelestialSelection.BORDER_WIDTH + GuiCelestialSelection.BORDER_EDGE_WIDTH + 138, 91, ColorUtil.to32BitColor(255, 255, 255, 255), true, false);
        }

    }


    @Override
    protected boolean teleportToSelectedBody()
    {
        this.possibleBodies = this.shuttlePossibleBodies;
        if (this.selectedBody != null)
        {
            if (this.selectedBody.getReachable() && this.possibleBodies != null && this.possibleBodies.contains(this.selectedBody))
            {
                try
                {
                    Integer dimensionID = null;

                    if (this.selectedBody instanceof Satellite)
                    {
                        if (this.spaceStationMap == null)
                        {
                            GCLog.severe("Please report as a BUG: spaceStationIDs was null.");
                            return false;
                        }
                        Satellite selectedSatellite = (Satellite) this.selectedBody;
                        Integer mapping = this.spaceStationMap.get(getSatelliteParentID(selectedSatellite)).get(this.selectedStationOwner).getStationDimensionID();
                        //No need to check lowercase as selectedStationOwner is taken from keys.
                        if (mapping == null)
                        {
                            GCLog.severe("Problem matching player name in space station check: " + this.selectedStationOwner);
                            return false;
                        }
                        int spacestationID = mapping;
                        dimensionID = spacestationID;
                    }
                    else
                    {
                        dimensionID = this.selectedBody.getDimensionID();
                    }
                    /*
                    if (dimension.contains("$"))
                    {
                        this.mc.gameSettings.thirdPersonView = 0;
                    }
                    if(dimensionID == null) {
                        return false;
                    }
                     */
                    AmunRa.packetPipeline.sendToServer(new PacketSimpleAR(PacketSimpleAR.EnumSimplePacket.S_TELEPORT_SHUTTLE, new Object[] { dimensionID }));
                    mc.displayGuiScreen(null);
                    return true;
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }


    @Override
    protected void mouseClicked(int x, int y, int button)
    {
        // exitBtnArea

        boolean clickHandled = false;
        // CelestialBody curSelection = this.selectedBody;

        if (!this.mapMode)
        {
            if(exitBtnArea.isWithin(x, y)) {
                cancelLaunch();
                clickHandled = true;
            } else if (buildMsBtnArea.isWithin(x, y)) {
                if (this.selectedBody != null)
                {
                    SpaceStationRecipe recipe = RecipeHelper.mothershipRecipe;
                    if (recipe != null && this.canCreateMothership(this.selectedBody) && !createMothershipButtonDisabled)
                    {
                        if (recipe.matches(this.mc.thePlayer, false) || this.mc.thePlayer.capabilities.isCreativeMode)
                        {
                            createMothershipButtonDisabled = true;
                            AmunRa.packetPipeline.sendToServer(new PacketSimpleAR(PacketSimpleAR.EnumSimplePacket.S_CREATE_MOTHERSHIP, new Object[] {
                                    AstronomyHelper.getOrbitableBodyName(this.selectedBody)
                            }));
                        }
                        clickHandled = true;
                    }
                }
            }
        }

        if(!clickHandled) {
            super.mouseClicked(x, y, button);

        }
    }

    protected void cancelLaunch()
    {
        AmunRa.packetPipeline.sendToServer(new PacketSimpleAR(PacketSimpleAR.EnumSimplePacket.S_CANCEL_SHUTTLE));
        /*if(mc.thePlayer.ridingEntity != null) {
            System.out.print("yes, riding");
        }
        mc.displayGuiScreen(null);*/
    }

    @Override
    public void mothershipCreationFailed() {

        createMothershipButtonDisabled = false;
    }

    @Override
    public void newMothershipCreated(Mothership ship) {
        super.newMothershipCreated(ship);

        if(ship.isPlayerOwner(this.mc.thePlayer)) {
            this.selectAndZoom(ship);
        }

        createMothershipButtonDisabled = false;
    }
}
