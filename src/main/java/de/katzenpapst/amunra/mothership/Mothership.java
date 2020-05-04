package de.katzenpapst.amunra.mothership;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import de.katzenpapst.amunra.AmunRa;
import de.katzenpapst.amunra.helper.AstronomyHelper;
import de.katzenpapst.amunra.helper.PlayerID;
import de.katzenpapst.amunra.tick.TickHandlerServer;
import micdoodle8.mods.galacticraft.api.galaxies.CelestialBody;
import micdoodle8.mods.galacticraft.api.galaxies.GalaxyRegistry;
import micdoodle8.mods.galacticraft.api.galaxies.Moon;
import micdoodle8.mods.galacticraft.api.galaxies.Planet;
import micdoodle8.mods.galacticraft.api.galaxies.Satellite;
import micdoodle8.mods.galacticraft.api.galaxies.SolarSystem;
import micdoodle8.mods.galacticraft.api.galaxies.Star;
import micdoodle8.mods.galacticraft.core.proxy.ClientProxyCore;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.Constants;

public class Mothership extends CelestialBody {

    public enum PermissionMode {
        ALL,        // everyone
        NONE,       // only owner
        WHITELIST,  // owner+everyone on list
        BLACKLIST   // everyone except people on list
    }

    // protected List<PlayerID> playerList = new ArrayList<PlayerID>();

    protected Set<PlayerID> playerSetLanding = new HashSet<PlayerID>();
    protected Set<PlayerID> playerSetUsage = new HashSet<PlayerID>();

    protected PermissionMode permModeLanding = PermissionMode.NONE;
    protected PermissionMode permModeUsage   = PermissionMode.NONE;

    protected PlayerID owner;

    protected String msName = "";

    protected CelestialBody previousParent;
    protected CelestialBody currentParent;

    protected long travelTimeTotal;
    protected long travelTimeRemaining;
    //protected long timestampDeparture;
    //protected long timestampArrival;

    protected boolean inTransit = false;

    protected int mothershipId;


    // the backslash should definitely not be valid for unlocalizedName
    public static final String nameSeparator = "\\";

    public Mothership(int id, PlayerID owner) {
        super("mothership_"+id);
        mothershipId = id;

        this.owner = owner;

        this.setBodyIcon(new ResourceLocation(AmunRa.ASSETPREFIX, "textures/gui/mothership_icons/0.png"));
        this.setRelativeOrbitTime(5);
    }

    public Mothership(int id, UUID ownerUUID, String ownerName) {
        this(id, new PlayerID(ownerUUID, ownerName));
    }

    public boolean setParent(CelestialBody parent) {
        if(parent instanceof Satellite) {
            return false;
        }

        currentParent = parent;
        inTransit = false;
        travelTimeRemaining = 0;

        return true;
    }

    public Set<PlayerID> getPlayerListLanding() {
        return playerSetLanding;
    }

    public void addPlayerToListLanding(PlayerID pi) {
        playerSetLanding.add(pi);
    }

    public void setPlayerListLanding(Collection<PlayerID> list) {
        playerSetLanding.clear();
        playerSetLanding.addAll(list);
        //playerSetLanding = list;
    }

    public Set<PlayerID> getPlayerListUsage() {
        return playerSetUsage;
    }

    public void addPlayerToListUsage(PlayerID pi) {
        playerSetUsage.add(pi);
    }

    public void setPlayerListUsage(Collection<PlayerID> list) {
        playerSetUsage.clear();
        playerSetUsage.addAll(list);
    }

    public PermissionMode getLandingPermissionMode() {
        return this.permModeLanding;
    }

    public void setLandingPermissionMode(PermissionMode mode) {
        this.permModeLanding = mode;
    }

    public PermissionMode getUsagePermissionMode() {
        return this.permModeUsage;
    }

    public void setUsagePermissionMode(PermissionMode mode) {
        this.permModeUsage = mode;
    }

    @Override
    public boolean getReachable()
    {
        return this.isReachable;
    }

    @Override
    public void setUnreachable()
    {
        // noop
        this.isReachable = true;
    }

    @Override
    public String getLocalizedName()
    {
        if(msName.isEmpty()) {
            msName = String.format(StatCollector.translateToLocal("mothership.default.name"), mothershipId);
        }
        return msName;
    }

    public void setLocalizedName(String newName)
    {
        msName = newName;
    }


    public boolean isInTransit() {
        return inTransit;
    }

    /**
     * Returns the parent, if stationary
     * @return
     */
    public CelestialBody getParent() {
        if(this.inTransit) {
            return null;
        }
        return currentParent;
    }

    /**
     * Returns the previous parent, if in transit
     * @return
     */
    public CelestialBody getSource() {
        if(this.inTransit) {
            return previousParent;
        }
        return null;
    }

    /**
     * Returns the destination or the parent if stationary
     * @return
     */
    public CelestialBody getDestination() {
        return currentParent;
    }

    /**
     * Only do stuff regarding this object itself, send the packets and stuff at someplace else.
     * This should only ever be called from the the MothershipWorldProvider!
     *
     * @param target
     * @return
     */
    public boolean startTransit(CelestialBody target, long travelTime) {
        if(!canBeOrbited(target) || this.isInTransit()) {
            return false;
        }

        if(travelTime > AmunRa.config.mothershipMaxTravelTime) {
            return false;
        }

        FMLLog.info("Mothership %d will begin transit to %s", this.getID(), target.getName());

        // allow change of route in mid-transit, too
        this.inTransit = true;
        this.travelTimeTotal = travelTime;
        this.travelTimeRemaining = this.travelTimeTotal;
        this.previousParent = this.currentParent;
        this.currentParent = target;
        TickHandlerServer.mothershipData.updateOrbitsFor(previousParent);
        // mark the MS data dirty here?

        return true;
    }

    /**
     * This should only ever be called from the the MothershipWorldProvider!
     * @return
     */
    public boolean endTransit()
    {
        if(!this.inTransit) {
            return false;
        }
        FMLLog.info("Mothership %d finished transit", this.getID());
        this.previousParent = null;
        this.travelTimeRemaining = 0;
        this.travelTimeTotal = 0;
        this.inTransit = false;
        TickHandlerServer.mothershipData.updateOrbitsFor(currentParent);

        return true;
    }

    public boolean forceArrival()
    {
        if(!this.inTransit) {
            return false;
        }
        this.travelTimeRemaining = 0;
        return true;
    }

    /**
     * Gets the MothershipWorldProvider of this mothership. Will probably load the world if it isn't. Server only.
     * @return
     */
    // @ SideOnly(Side.SERVER)
    public MothershipWorldProvider getWorldProviderServer() {
        MinecraftServer mcServer = FMLCommonHandler.instance().getMinecraftServerInstance();
        WorldServer ws = mcServer.worldServerForDimension(this.getDimensionID());
        if(ws == null || !(ws.provider instanceof MothershipWorldProvider)) {
            return null;
        }
        return (MothershipWorldProvider) ws.provider;
    }

    /**
     * Basically checks if the current world is the world of this MS, if yes, returns the provider. Client only.
     * @return
     */
    @ SideOnly(Side.CLIENT)
    public MothershipWorldProvider getWorldProviderClient() {
        World ws = ClientProxyCore.mc.theWorld;
        if (ws != null && ws.provider.dimensionId == this.getDimensionID())
        {
            return (MothershipWorldProvider) ws.provider;
        }
        return null;
    }

    /*
    public double getSpeed() {
        // 0.001 makes sun <--> earth have 1kt (50 seconds), and earth<-->ra 13,15Mt (182 hours)
        return 0.001D; // for now
    }*/

    public double getTravelDistanceTo(CelestialBody target) {
        return AstronomyHelper.getDistance(currentParent, target);
    }
/*
    public int getTravelTimeTo(CelestialBody target) {
        return getTravelTimeTo(getTravelDistanceTo(target), this.getSpeed());
    }
*/
    /*public int getTravelTimeTo(double distance, double speed) {

        return (int) Math.ceil(distance/speed) + 80;
    }*/

    /**
     * For rendering bars and such
     *
     * @param barLength
     * @return
     */
    public int getScaledTravelTime(int barLength) {
        float remain = this.getRemainingTravelTime();
        float total = this.getTotalTravelTime();
        float relative = remain/total;
        float scaled = (1-relative)*barLength;
        return (int)(scaled);
    }

    public PlayerID getOwner() {
        return owner;
    }

    @Override
    public int getID() {
        return mothershipId;
    }

    @Override
    public String getUnlocalizedNamePrefix() {
        return "mothership";
    }

    public static boolean canBeOrbited(CelestialBody body) {
        return (
            (AmunRa.config.mothershipMaxTier >= body.getTierRequirement()) &&
            (body instanceof Planet) || (body instanceof Moon) || (body instanceof Star) &&
            !AmunRa.config.mothershipBodiesNoOrbit.contains(body.getName())
        );
    }

    public CelestialBody setDimensionInfo(int dimID)
    {
        return this.setDimensionInfo(dimID, MothershipWorldProvider.class);
    }

    /**
     * Finds mothership-able bodies by "url", aka "solarSystem\planet\moon"
     *
     * @param bodyName
     * @return
     */
    public static CelestialBody findBodyByNamePath(String bodyName) {

        SolarSystem curSys = null;
        CelestialBody body = null;
        CelestialBody moon = null;

        String[] parts = bodyName.split(Pattern.quote(nameSeparator));

        for(int i=0;i<parts.length;i++) {
            switch(i) {
            case 0:
                //
                curSys = GalaxyRegistry.getRegisteredSolarSystems().get(parts[i]);
                body = curSys.getMainStar();
                break;
            case 1:
                body = GalaxyRegistry.getRegisteredPlanets().get(parts[i]);
                // sanity check
                if(!((Planet)body).getParentSolarSystem().equals(curSys)) {
                    throw new RuntimeException("Planet "+body.getName()+" is not in "+bodyName);
                }
                break;
            case 2:
                moon = GalaxyRegistry.getRegisteredMoons().get(parts[i]);
                // sanity checks
                if(!((Moon)moon).getParentPlanet().equals(body)) {
                    throw new RuntimeException("Moon "+moon.getName()+" is not in "+bodyName);
                }
                // at this point, we are done anyway
                return moon;
            }
        }
        if(body == null) {
            throw new RuntimeException("Could not find body for "+bodyName);
        }
        return body;
    }

    /**
     * Finds mothership-able bodies by galacticraft body name, aka the english name...
     *
     * @param bodyName
     * @return
     */
    public static CelestialBody findBodyByGCBodyName(String bodyName) {
        Collection<SolarSystem> sysList = GalaxyRegistry.getRegisteredSolarSystems().values();
        CelestialBody body;
        for(SolarSystem sys: sysList) {
            body = sys.getMainStar();
            if(body.getName().equals(bodyName)) {
                return body;
            }
        }
        body = GalaxyRegistry.getRegisteredPlanets().get(bodyName);
        if(body != null) {
            return body;
        }

        body = GalaxyRegistry.getRegisteredMoons().get(bodyName);
        return body;
    }

    /**
     * Automatically
     * @param str
     * @return
     */
    public static CelestialBody findBodyByString(String str) {
        if(str.contains(nameSeparator)) {
            return findBodyByNamePath(str);
        }
        return findBodyByGCBodyName(str);
    }

    public static Mothership createFromNBT(NBTTagCompound data) {
        if(!data.hasKey("id") || !data.hasKey("owner")) {
            throw new RuntimeException("Invalid Mothership!");
        }
        int id = data.getInteger("id");
        String ownerUUID = data.getString("owner");
        String ownerName = data.getString("ownerName");

        Mothership result = new Mothership(id, UUID.fromString(ownerUUID), ownerName);

        // these must always be set, a mothership is invalid without

        String parentId = data.getString("parentName");
        CelestialBody foundParent = findBodyByNamePath(parentId);

        String prevParentId = data.getString("prevParentName");
        if(!prevParentId.isEmpty()) {
            result.previousParent = findBodyByNamePath(prevParentId);
        }

        result.currentParent = foundParent;
        result.inTransit = data.getBoolean("inTransit");
        result.travelTimeRemaining = data.getLong("travelTimeRemaining");
        result.travelTimeTotal = data.getLong("travelTimeTotal");

        result.setDimensionInfo(data.getInteger("dim"));
        result.isReachable = true;

        //float distance = setFloat("orbitDistance", this.getRelativeDistanceFromCenter().unScaledDistance);

        result.readSettingsFromNBT(data);

        return result;
    }

    /*public void readFromNBT(NBTTagCompound data) {
        String parentId = data.getString("parentName");
        CelestialBody foundParent = findBodyByNamePath(parentId);

        String prevParentId = data.getString("prevParentName");
        if(!prevParentId.isEmpty()) {
            previousParent = findBodyByNamePath(prevParentId);
        }

        currentParent = foundParent;
        inTransit = data.getBoolean("inTransit");
        travelTimeRemaining = data.getInteger("travelTimeRemaining");
        travelTimeTotal = data.getInteger("travelTimeTotal");
        msName = data.getString("name");
        setDimensionInfo(data.getInteger("dim"));
        isReachable = true;
    }*/

    public void writeToNBT(NBTTagCompound data) {
        data.setString("owner", this.owner.getUUID().toString());
        data.setString("ownerName", this.owner.getName());
        data.setInteger("id", this.mothershipId);
        data.setInteger("dim", this.dimensionID);


        String parentId = AstronomyHelper.getOrbitableBodyName(this.currentParent);
        data.setString("parentName", parentId);

        if(this.previousParent != null) {
            String prevParentId = AstronomyHelper.getOrbitableBodyName(this.previousParent);
            data.setString("prevParentName",prevParentId);
        }

        data.setBoolean("inTransit", this.inTransit);
        data.setLong("travelTimeRemaining", this.travelTimeRemaining);
        data.setLong("travelTimeTotal", this.travelTimeTotal);



        writeSettingsToNBT(data);
    }


    /**
     * "Settings" are things which a player can change
     * @param data
     */
    public void readSettingsFromNBT(NBTTagCompound data) {
        if(data.hasKey("bodyIcon")) {
            this.setBodyIcon(new ResourceLocation(data.getString("bodyIcon")));
        }
        if(data.hasKey("name")) {
            this.msName = data.getString("name");
        }

        NBTTagList list = data.getTagList("playerList", Constants.NBT.TAG_COMPOUND);
        playerSetLanding = new HashSet<PlayerID>();
        //playerList.clear();
        for(int i=0;i<list.tagCount();i++) {
            NBTTagCompound playerData = list.getCompoundTagAt(i);
            PlayerID pd = new PlayerID(playerData);
            playerSetLanding.add(pd);
        }

        if(data.hasKey("permissionMode")) {
            int modeIndex = data.getInteger("permissionMode");
            permModeLanding = PermissionMode.values()[modeIndex];
        }

        list = data.getTagList("playerListUse", Constants.NBT.TAG_COMPOUND);
        playerSetUsage = new HashSet<PlayerID>();
        for(int i=0;i<list.tagCount();i++) {
            NBTTagCompound playerData = list.getCompoundTagAt(i);
            PlayerID pd = new PlayerID(playerData);
            playerSetUsage.add(pd);
        }

        if(data.hasKey("permissionModeUse")) {
            int modeIndex = data.getInteger("permissionModeUse");
            permModeUsage = PermissionMode.values()[modeIndex];
        }
    }

    public void writeSettingsToNBT(NBTTagCompound data) {
        data.setString("bodyIcon", this.getBodyIcon().toString());
        data.setString("name", this.msName);

        NBTTagList list = new NBTTagList();

        for(PlayerID p: playerSetLanding) {
            list.appendTag(p.getNbt());
        }
        data.setTag("playerList", list);

        list = new NBTTagList();

        for(PlayerID p: playerSetUsage) {
            list.appendTag(p.getNbt());
        }
        data.setTag("playerListUse", list);

        data.setInteger("permissionMode", this.permModeLanding.ordinal());
        data.setInteger("permissionModeUse", this.permModeUsage.ordinal());
    }

    public long getTotalTravelTime() {
        return this.travelTimeTotal;
    }

    public long getRemainingTravelTime() {
        return this.travelTimeRemaining;
    }

    public long modRemainingTravelTime(int mod) {
        this.travelTimeRemaining += mod;
        return this.travelTimeRemaining;
    }

    public void setRemainingTravelTime(int set) {
        this.travelTimeRemaining = set;
    }

    /**
     * Returns whenever the given player is the owner of this mothership
     *
     * @param player
     * @return
     */
    public boolean isPlayerOwner(EntityPlayer player) {
        return owner.isSameUser(player);
    }

    /**
     * Returns whenever the given player is the owner of this mothership
     *
     * @param player
     * @return
     */
    public boolean isPlayerOwner(PlayerID player) {
        return owner.equals(player);
    }

    public boolean isPlayerUsagePermitted(EntityPlayer player) {

        if(player.capabilities.isCreativeMode) {
            return true;
        }

        return isPlayerPermitted(player, permModeUsage);
    }

    /**
     * Returns whenever the given player is permitted to land on this mothership
     *
     * @param player
     * @return
     */
    public boolean isPlayerLandingPermitted(EntityPlayer player) {

        if(player.capabilities.isCreativeMode) {
            return true;
        }

        return isPlayerPermitted(player, permModeLanding);
    }

    protected boolean isPlayerPermitted(EntityPlayer player, PermissionMode mode) {
        PlayerID playerId = new PlayerID(player);

        switch(this.permModeLanding) {
        case ALL:
            return true;
        case BLACKLIST:
            return this.isPlayerOwner(playerId) || !this.playerSetLanding.contains(playerId);
        case WHITELIST:
            return this.isPlayerOwner(playerId) || this.playerSetLanding.contains(playerId);
        case NONE:
        default:
            return this.isPlayerOwner(playerId);
        }
    }
}
