package dev.sakhno.regnum.nations.client.ui;

import dev.sakhno.regnum.nations.networking.PoliticalMapPayloads.*;
import dev.sakhno.regnum.nations.networking.AtlasPayloads;
import dev.sakhno.regnum.nations.client.terrain.AtlasTerrain;
import dev.sakhno.regnum.nations.client.ui.RoyalControlButton.Icon;
import java.util.*;
import dev.sakhno.regnum.nations.networking.KingdomManagementPayloads;
import dev.sakhno.regnum.nations.networking.KingdomManagementPayloads.Action;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import org.lwjgl.glfw.GLFW;
import static dev.sakhno.regnum.nations.client.ui.KingdomUi.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class PoliticalMapScreen extends RoyalScreen {
    private static final int MAP_TOP=48,MAP_BOTTOM=60,LEGEND_ROW=16;
    private final KingdomManagementScreen parent;
    private boolean manageMode;
    private KingdomManagementPayloads.State management;
    private KingdomManagementPayloads.Selection details;
    private UUID ownSettlement;
    private UUID viewedCity;
    private RoyalButton territoryAction;
    private Button refreshButton;
    private RoyalControlButton modeButton;
    private RoyalControlButton layersButton;
    private MapCanvas canvas;
    private boolean actionFailed;
    private static final String[] LEGEND_KEYS={"map_key_personal","map_key_selected","map_key_unknown"};
    private int selectedX, selectedZ, detailsSequence;
    private net.minecraft.resources.ResourceLocation selectedDimension;
    private boolean selected, needsDetails, actionPending, refreshDue, dragged;
    private long detailsRequested, detailsAt;
    private static long lastMapRequest;
    private boolean wasStanding, fitNextSnapshot = true;
    private double pressX, pressY;
    private Component feedback = Component.empty();
    private int sequence;
    private Snapshot snapshot;
    private final Map<Long, Cell> cells = new HashMap<>();
    private double centerX, centerZ, zoom = 32;
    private static final double MIN_ZOOM = 4, MAX_ZOOM = 64;
    private final int top=MAP_TOP;
    private int legendOffset;
    private boolean panning;
    private int layer; // 0: terrain + politics; 1: politics; 2: terrain
    private List<AtlasPayloads.CityMarker> cityMarkers = List.of();
    private long requested;
    private String state = "loading";
    private int right,bottom,legendX,legendWidth,legendListY,legendKeyY,toolbarY,inspectorY,headerTextWidth;
    public PoliticalMapScreen() { this(null,false); }
    public PoliticalMapScreen(KingdomManagementScreen parent, boolean manageMode) {
        super(text("title")); this.parent = parent; this.manageMode = manageMode;
        management = parent == null ? null : parent.state();
        if (management != null) {
            ownSettlement=management.ownSettlement(); viewedCity=management.city()==null?null:management.city().id();
        }
    }
    private static Component text(String key, Object... args) { return Component.translatable("map.regnum-nations." + key, args); }
    @Override protected void init() {
        panning = false;
        int legendColumn=Math.clamp(width/5,76,112);
        right=width-legendColumn-MARGIN-GAP; bottom=height-MAP_BOTTOM;
        legendX=right+GAP*2; legendWidth=width-MARGIN-legendX;
        legendListY=top+GAP*2+LINE; legendKeyY=bottom-MARGIN-LEGEND_KEYS.length*(LINE+2);
        toolbarY=height-MARGIN-CONTROL; inspectorY=bottom+4;
        layoutCenterX=Double.NaN;
        canvas=addRenderableWidget(new MapCanvas());
        Component back=KingdomUi.text("back_kingdom"),view=KingdomUi.text("mode_view"),manage=KingdomUi.text("mode_manage");
        int[] navigation=AtlasWidgets.rowWidths(font,(width-MARGIN*2)/2,CONTROL*2,back,font.width(manage)>font.width(view)?manage:view);
        int navigationX=width-MARGIN-CONTROL-GAP-navigation[0]-GAP-navigation[1],navigationY=top-CONTROL-MARGIN;
        headerTextWidth=navigationX-GAP-MARGIN*2;
        addRenderableWidget(new RoyalControlButton(navigationX,navigationY,navigation[0],back,Icon.NONE,false,b -> showOverview()));
        modeButton = addRenderableWidget(new RoyalControlButton(navigationX+navigation[0]+GAP,navigationY,navigation[1],KingdomUi.text(manageMode ? "mode_manage" : "mode_view"),Icon.NONE,false,b -> {
            manageMode = !manageMode;
            modeButton.setMessage(KingdomUi.text(manageMode ? "mode_manage" : "mode_view"));
            modeButton.selected(true);
            feedback = Component.empty(); updateActions();
        }));
        modeButton.selected(true);
        int actionWidth=Math.min((width-MARGIN*2)/3,Math.max(CONTROL*4,Math.max(font.width(KingdomUi.text("claim")),font.width(KingdomUi.text("unclaim")))+PAD*2));
        territoryAction = addRenderableWidget(new RoyalControlButton(width-MARGIN-actionWidth,inspectorY+2,actionWidth,KingdomUi.text("claim"),Icon.NONE,false,
                b -> sendDetails(releasing() ? Action.UNCLAIM : Action.CLAIM)));
        territoryAction.active = false; territoryAction.visible = manageMode;
        territoryAction.style(releasing()?ButtonStyle.DANGER:ButtonStyle.PRIMARY);
        // Keep the zoom tools close together, with wider space between functional groups.
        int groupGap=GAP+2,zoomGap=2;
        int flexibleWidth=width-MARGIN*2-CONTROL*3-groupGap*2-zoomGap*2;
        int fitWidth=Math.min(Math.max(CONTROL*2,font.width(text("fit"))+16),flexibleWidth/2);
        int layersWidth=Math.min(Math.max(CONTROL*3,font.width(text("layers"))+32),flexibleWidth-fitWidth);
        int toolX=MARGIN;
        refreshButton = addRenderableWidget(new RoyalControlButton(toolX,toolbarY,CONTROL,text("refresh"),Icon.REFRESH,true,b -> {
            fitNextSnapshot = true; request(); needsDetails = true; feedback = Component.empty();
        }));
        toolX+=CONTROL+groupGap;
        addRenderableWidget(new RoyalControlButton(toolX,toolbarY,CONTROL,KingdomUi.text("zoom_out"),Icon.MINUS,true,
                b -> zoom=Math.max(MIN_ZOOM,zoom/1.25)));
        toolX+=CONTROL+zoomGap;
        addRenderableWidget(new RoyalControlButton(toolX,toolbarY,CONTROL,KingdomUi.text("zoom_in"),Icon.PLUS,true,
                b -> zoom=Math.min(MAX_ZOOM,zoom*1.25)));
        toolX+=CONTROL+zoomGap;
        addRenderableWidget(new RoyalControlButton(toolX,toolbarY,fitWidth,text("fit"),Icon.NONE,false,b -> fitClaims()));
        toolX+=fitWidth+groupGap;
        layersButton=addRenderableWidget(new RoyalControlButton(toolX,toolbarY,layersWidth,text("layers"),Icon.LAYERS,false,b -> {
            layer=(layer+1)%3;
            updateLayerButton();
        }));
        updateLayerButton();
        addRenderableWidget(new RoyalControlButton(width-MARGIN-CONTROL,MARGIN,CONTROL,text("close"),Icon.CLOSE,true,b -> onClose()));
        setInitialFocus(canvas);
        if (requested == 0) request();
        if (!selected && minecraft.player != null && minecraft.level != null) {
            var pos = minecraft.player.chunkPosition(); select(pos.x,pos.z);
        }
    }
    private void updateLayerButton() {
        Component description=text("layer_"+layer);
        layersButton.layerState(layer,description);
    }
    private void request() {
        if (minecraft.player == null) return;
        if (System.currentTimeMillis()-lastMapRequest < 550) { refreshDue = true; return; }
        refreshDue = false; lastMapRequest = System.currentTimeMillis();
        sequence = KingdomUi.nextSequence();
        requested = System.currentTimeMillis(); state = "loading"; snapshot = null; cells.clear(); cityMarkers=List.of();
        if (fitNextSnapshot) {
            centerX = minecraft.player.getX() / 16; centerZ = minecraft.player.getZ() / 16;
            legendOffset = 0;
        }
        if (selectedDimension != null && !selectedDimension.equals(minecraft.level.dimension().location())) {
            var pos = minecraft.player.chunkPosition(); select(pos.x,pos.z);
        }
        if (!ClientPlayNetworking.canSend(Request.TYPE)) { state = "error"; return; }
        ClientPlayNetworking.send(new Request(sequence));
    }
    public void accept(Snapshot data) {
        if (data.sequence() != sequence || minecraft.level == null || !data.dimension().equals(minecraft.level.dimension().location())) return;
        snapshot = data;
        cells.clear();
        for (Cell c : data.cells()) {
            cells.put(key(c.x(), c.z()), c);
        }
        if (fitNextSnapshot) fitClaims();
        fitNextSnapshot = true;
        state = data.status() == 2 ? "error" : data.cells().isEmpty() ? "empty" : data.status() == 1 ? "truncated" : "ready";
    }
    public void acceptMarkers(AtlasPayloads.Markers data) {
        if (data.sequence()!=sequence || snapshot==null || minecraft.level==null
                || !data.dimension().equals(snapshot.dimension())
                || !data.dimension().equals(minecraft.level.dimension().location())) return;
        cityMarkers=data.cities().stream().filter(marker -> {
            Cell cell=cells.get(key(marker.pos().getX()>>4,marker.pos().getZ()>>4));
            return cell!=null && cell.owner().equals(marker.city());
        }).toList();
    }
    public void invalidate() {
        if (parent!=null) parent.invalidate();
        management=null; ownSettlement=null; details=null;
        detailsSequence=KingdomUi.nextSequence(); detailsRequested=0; actionPending=false; needsDetails=true;
        sequence=KingdomUi.nextSequence(); snapshot=null; cells.clear(); cityMarkers=List.of();
        state="loading"; fitNextSnapshot=false; refreshDue=true;
        feedback=Component.empty(); actionFailed=false; updateActions();
    }
    private static long key(int x, int z) { return ((long)x << 32) ^ (z & 0xffffffffL); }
    private boolean inside(double x, double y) { return x >= 8 && x < right && y >= top && y < bottom; }
    private double originX() { return (8 + right) / 2.0 - centerX * zoom; }
    private double originZ() { return (top + bottom) / 2.0 - centerZ * zoom; }
    private boolean same(Cell c, int x, int z) { Cell other = cells.get(key(x,z)); return other != null && c.owner().equals(other.owner()); }
    private boolean samePolity(Cell c,int x,int z) {
        Cell other=cells.get(key(x,z));
        return other!=null&&(c.sovereign()==null?other.sovereign()==null&&c.owner().equals(other.owner()):c.sovereign().equals(other.sovereign()));
    }
    private void fitClaims() {
        if (minecraft.player == null) return;
        centerX = minecraft.player.getX() / 16; centerZ = minecraft.player.getZ() / 16;
        double spanX = 2, spanZ = 2;
        for (Cell c : cells.values()) {
            spanX = Math.max(spanX, Math.max(Math.abs(c.x() - centerX), Math.abs(c.x() + 1 - centerX)) * 2);
            spanZ = Math.max(spanZ, Math.max(Math.abs(c.z() - centerZ), Math.abs(c.z() + 1 - centerZ)) * 2);
        }
        zoom = Math.clamp(Math.min((right - 8) / (spanX + 2), (bottom - top) / (spanZ + 2)), MIN_ZOOM, MAX_ZOOM);
    }
    private Component dimensionName() { return KingdomUi.dimension(minecraft.level.dimension().location()); }
    private static int subdued(int color) { return dev.sakhno.regnum.nations.kingdom.PoliticalColors.subdued(color); }
    @Override public void tick() {
        if (minecraft.player == null || minecraft.level == null) { minecraft.setScreen(null); return; }
        if (refreshDue && !actionPending && System.currentTimeMillis()-lastMapRequest >= 550) request();
        if (needsDetails && !actionPending && detailsRequested == 0 && System.currentTimeMillis()-detailsAt >= 175) sendDetails(Action.INSPECT);
        if (detailsRequested != 0 && System.currentTimeMillis()-detailsRequested > 10000) {
            detailsRequested = 0; actionPending = false; details = null; feedback = KingdomUi.text("timeout"); actionFailed=true;
        }
        boolean standing = standingInSelection();
        if (standing != wasStanding && !actionPending) { details = null; needsDetails = true; }
        wasStanding = standing;
        refreshButton.active = !actionPending && !state.equals("loading") && System.currentTimeMillis()-lastMapRequest >= 550;
        updateActions();
        if (requested != 0 && state.equals("loading") && !refreshDue && System.currentTimeMillis() - requested > 10000) state = "error";
        if (snapshot != null && minecraft.level != null && !snapshot.dimension().equals(minecraft.level.dimension().location())) { snapshot = null; cells.clear(); cityMarkers=List.of(); state = "stale"; details = null; needsDetails = false; }
    }
    private record Tile(Cell cell,int left,int top,int right,int bottom,int borders,int politicalBorders) { }
    private record MapLabel(String name,int x,int y,int width) { }
    private final List<Tile> tiles=new ArrayList<>();
    private final List<Cell> legendEntries=new ArrayList<>();
    private final List<String> legendNames=new ArrayList<>();
    private final List<MapLabel> labels=new ArrayList<>();
    private record MarkerPoint(AtlasPayloads.CityMarker marker,Cell cell,int x,int y) { }
    private record MapMarker(MarkerPoint point,List<MarkerPoint> members) { }
    private final List<MapMarker> mapMarkers=new ArrayList<>();
    private List<AtlasPayloads.CityMarker> layoutCityMarkers;
    private double layoutCenterX=Double.NaN,layoutCenterZ,layoutZoom;
    private int layoutRight,layoutBottom,layoutPlayerX,layoutPlayerZ;
    private Snapshot layoutSnapshot;

    /** Geometry, label widths and legend order change with the viewport/snapshot, not each frame. */
    private void prepareLayout(int playerX,int playerZ) {
        if (layoutCenterX==centerX && layoutCenterZ==centerZ && layoutZoom==zoom && layoutRight==right
                && layoutBottom==bottom && layoutSnapshot==snapshot && layoutCityMarkers==cityMarkers && layoutPlayerX==playerX && layoutPlayerZ==playerZ) return;
        layoutCenterX=centerX; layoutCenterZ=centerZ; layoutZoom=zoom; layoutRight=right; layoutBottom=bottom;
        layoutSnapshot=snapshot; layoutCityMarkers=cityMarkers; layoutPlayerX=playerX; layoutPlayerZ=playerZ;
        tiles.clear(); legendEntries.clear(); legendNames.clear(); labels.clear(); mapMarkers.clear();
        Map<UUID,List<Tile>> groups=new HashMap<>();
        double ox=originX(),oz=originZ();
        for (Cell c:cells.values()) {
            int l=(int)Math.floor(ox+c.x()*zoom),t=(int)Math.floor(oz+c.z()*zoom);
            int r=(int)Math.floor(ox+(c.x()+1)*zoom),b=(int)Math.floor(oz+(c.z()+1)*zoom);
            if (r<8 || l>=right || b<top || t>=bottom) continue;
            int borders=(!same(c,c.x()-1,c.z())?1:0)|(!same(c,c.x()+1,c.z())?2:0)
                    |(!same(c,c.x(),c.z()-1)?4:0)|(!same(c,c.x(),c.z()+1)?8:0);
            int politicalBorders=(!samePolity(c,c.x()-1,c.z())?1:0)|(!samePolity(c,c.x()+1,c.z())?2:0)
                    |(!samePolity(c,c.x(),c.z()-1)?4:0)|(!samePolity(c,c.x(),c.z()+1)?8:0);
            Tile tile=new Tile(c,l,t,r,b,borders,politicalBorders); tiles.add(tile);
            groups.computeIfAbsent(c.owner(),id -> new ArrayList<>()).add(tile);
        }
        for (List<Tile> group:groups.values()) legendEntries.add(group.getFirst().cell());
        legendEntries.sort(Comparator.comparing(Cell::name).thenComparing(Cell::owner));
        for (Cell c:legendEntries) legendNames.add(ellipsize(font,Component.literal(c.name()),legendWidth-15));
        // Retain exact anchors. Nearby cities share one hit target rather than moving pins.
        var points=new ArrayList<MarkerPoint>();
        for (var marker:cityMarkers) {
            Cell cell=cells.get(key(marker.pos().getX()>>4,marker.pos().getZ()>>4));
            if (cell==null || !cell.owner().equals(marker.city())) continue;
            int x=(int)Math.floor(ox+(marker.pos().getX()+0.5)/16*zoom);
            int y=(int)Math.floor(oz+(marker.pos().getZ()+0.5)/16*zoom);
            if (x<8 || x>=right || y<top || y>=bottom) continue;
            points.add(new MarkerPoint(marker,cell,x,y));
        }
        points.sort(Comparator.comparing((MarkerPoint p) -> !p.marker().capital()).thenComparing(p -> p.marker().city()));
        for (MarkerPoint point:points) {
            MapMarker cluster=null;
            for (MapMarker existing:mapMarkers) if (Math.abs(point.x()-existing.point().x())<24
                    && Math.abs(point.y()-existing.point().y())<17) { cluster=existing; break; }
            if (cluster!=null) cluster.members().add(point);
            else mapMarkers.add(new MapMarker(point,new ArrayList<>(List.of(point))));
        }
        var occupied=new ArrayList<int[]>();
        // Reserve the whole player chunk because the player can move inside it without a layout rebuild.
        int px=(int)(ox+playerX*zoom),pz=(int)(oz+playerZ*zoom);
        occupied.add(new int[]{px-6,pz-6,px+(int)zoom+7,pz+(int)zoom+7});
        occupied.add(new int[]{right-37,top,right,top+18});
        for (MapMarker marker:mapMarkers) {
            var p=marker.point();
            occupied.add(new int[]{p.x()-8,p.y()-8,p.x()+9+(marker.members().size()>1?8:0),p.y()+9});
        }
        if (zoom<10) return;
        for (MapMarker marker:mapMarkers) {
            var p=marker.point();
            int w=font.width(p.cell().name())+8;
            // Prefer beside the icon, then below/above. Suppress labels that do not fit.
            int[][] positions={{p.x()+11,p.y()-6},{p.x()-w-11,p.y()-6},{p.x()-w/2,p.y()+11},{p.x()-w/2,p.y()-24},
                    {px+(int)zoom+11,p.y()-6},{px-w-10,p.y()-6}};
            for (int i=0;i<positions.length;i++) {
                if (i>=4 && (p.marker().pos().getX()>>4!=playerX || p.marker().pos().getZ()>>4!=playerZ)) continue;
                int[] position=positions[i];
                int l=position[0],t=position[1];
                if (l<12 || l+w>right-4 || t<top+4 || t+13>bottom-4) continue;
                boolean overlaps=false;
                for (int[] box:occupied) if (l<box[2]+2 && l+w+2>box[0] && t<box[3]+2 && t+15>box[1]) { overlaps=true; break; }
                if (overlaps) continue;
                labels.add(new MapLabel(p.cell().name(),l,t,w)); occupied.add(new int[]{l,t,l+w,t+13}); break;
            }
        }
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        g.fill(0,0,width,height,BACKGROUND);
        AtlasGraphics.folio(g,0,0,width,height);
        Component scale=text("scale",(int)Math.round(zoom));
        int scaleX=width-MARGIN-CONTROL-GAP-font.width(scale),titleX=MARGIN+23;
        AtlasGraphics.mark(g,13,9,false);
        g.drawString(font,ellipsize(font,title,scaleX-GAP-titleX),titleX,10,AtlasGraphics.LIGHT,false);
        AtlasGraphics.divider(g,MARGIN+4,top-MARGIN,width-(MARGIN+4)*2);
        if (minecraft.player==null || minecraft.level==null) return;
        var pos=minecraft.player.chunkPosition();
        g.drawString(font,ellipsize(font,dimensionName().copy().append(" / ").append(text("position",pos.x,pos.z)),headerTextWidth),MARGIN*2,25,MUTED,false);
        g.fill(7,top-1,right+1,bottom+1,AtlasGraphics.ETCH);
        g.fill(right+7,top+4,right+8,bottom-4,AtlasGraphics.ETCH);
        prepareLayout(pos.x,pos.z);
        double ox=originX(),oz=originZ();
        g.enableScissor(8,top,right,bottom);
        AtlasGraphics.mapFog(g,8,top,right,bottom);
        if (layer==1 && snapshot!=null && snapshot.status()==0) {
            g.fill((int)Math.floor(ox+(snapshot.centerX()-snapshot.radius())*zoom),
                    (int)Math.floor(oz+(snapshot.centerZ()-snapshot.radius())*zoom),
                    (int)Math.floor(ox+(snapshot.centerX()+snapshot.radius()+1)*zoom),
                    (int)Math.floor(oz+(snapshot.centerZ()+snapshot.radius()+1)*zoom),WILDERNESS);
        }
        if (layer!=1) AtlasTerrain.draw(g,8,top,right,bottom,ox,oz,zoom);
        // The optional surveying grid is confined to the political-only layer.
        if (layer==1 && zoom>=8) {
            for (double x=ox+Math.ceil((8-ox)/zoom)*zoom;x<right;x+=zoom) g.fill((int)Math.floor(x),top,(int)Math.floor(x)+1,bottom,GRID);
            for (double z=oz+Math.ceil((top-oz)/zoom)*zoom;z<bottom;z+=zoom) g.fill(8,(int)Math.floor(z),right,(int)Math.floor(z)+1,GRID);
        }
        if (layer!=2) for (Tile tile:tiles) g.fill(tile.left(),tile.top(),tile.right(),tile.bottom(),
                layer==1?subdued(tile.cell().color()):(tile.cell().color()&0x00ffffff)|0x40000000);
        if (layer!=2) for (Tile tile:tiles) {
            int l=tile.left(),t=tile.top(),r=tile.right(),b=tile.bottom();
            // Fine city boundaries remain visible within a realm; sovereignty has a stronger contour.
            if ((tile.borders()&1)!=0) g.fill(l,t,l+1,b,AtlasGraphics.RULE);
            if ((tile.borders()&2)!=0) g.fill(r-1,t,r,b,AtlasGraphics.RULE);
            if ((tile.borders()&4)!=0) g.fill(l,t,r,t+1,AtlasGraphics.RULE);
            if ((tile.borders()&8)!=0) g.fill(l,b-1,r,b,AtlasGraphics.RULE);
            int edge=2;
            if ((tile.politicalBorders()&1)!=0) g.fill(l,t,l+edge,b,AtlasGraphics.SHADOW);
            if ((tile.politicalBorders()&2)!=0) g.fill(r-edge,t,r,b,AtlasGraphics.SHADOW);
            if ((tile.politicalBorders()&4)!=0) g.fill(l,t,r,t+edge,AtlasGraphics.SHADOW);
            if ((tile.politicalBorders()&8)!=0) g.fill(l,b-edge,r,b,AtlasGraphics.SHADOW);
            int color=tile.cell().color()|0xff000000;
            if ((tile.politicalBorders()&1)!=0) g.fill(l,t,l+1,b,color);
            if ((tile.politicalBorders()&2)!=0) g.fill(r-1,t,r,b,color);
            if ((tile.politicalBorders()&4)!=0) g.fill(l,t,r,t+1,color);
            if ((tile.politicalBorders()&8)!=0) g.fill(l,b-1,r,b,color);
            if (tile.cell().owner().equals(ownSettlement)) {
                g.fill(l+1,t+1,Math.min(r-1,l+5),t+2,GOLD);
                g.fill(l+1,t+1,l+2,Math.min(b-1,t+5),GOLD);
            }
        }
        int currentL=(int)Math.floor(ox+pos.x*zoom),currentT=(int)Math.floor(oz+pos.z*zoom);
        outline(g,currentL,currentT,(int)Math.floor(ox+(pos.x+1)*zoom),(int)Math.floor(oz+(pos.z+1)*zoom),GOLD);
        if (selected && selectedDimension.equals(minecraft.level.dimension().location())) {
            int l=(int)Math.floor(ox+selectedX*zoom),t=(int)Math.floor(oz+selectedZ*zoom);
            int r=(int)Math.floor(ox+(selectedX+1)*zoom),b=(int)Math.floor(oz+(selectedZ+1)*zoom);
            g.fill(l,t,r,b,0x18fff1cb);
            outline(g,l,t,r,b,AtlasGraphics.SHADOW,1);
            outline(g,l+1,t+1,r-1,b-1,SELECTED,1);
        }
        if (layer!=2) for (MapLabel label:labels) {
            g.fill(label.x(),label.y(),label.x()+label.width(),label.y()+13,LABEL_BACK);
            g.drawString(font,label.name(),label.x()+4,label.y()+3,INK,false);
        }
        if (layer!=2) for (MapMarker marker:mapMarkers) {
            var p=marker.point();
            AtlasGraphics.mapSettlement(g,p.x(),p.y(),p.marker().capital(),p.cell().color());
            if (marker.members().size()>1) {
                g.fill(p.x()+6,p.y()-8,p.x()+14,p.y()+2,LABEL_BACK);
                g.drawString(font,"+",p.x()+7,p.y()-7,AtlasGraphics.LIGHT,false);
            }
        }
        int px=(int)Math.floor(ox+minecraft.player.getX()/16*zoom),pz=(int)Math.floor(oz+minecraft.player.getZ()/16*zoom);
        AtlasGraphics.mapPlayer(g,px,pz,minecraft.player.getYRot());
        if (!state.equals("ready")) {
            String status=ellipsize(font,text(state),right-24);
            g.fill(10,bottom-14,Math.min(right-2,16+font.width(status)),bottom-2,LABEL_BACK);
            g.drawString(font,status,13,bottom-12,state.equals("error")?ERROR:MUTED,false);
        }
        g.disableScissor();
        g.fill(right-15,top+5,right-14,top+17,AtlasGraphics.BRASS);
        g.fill(right-17,top+7,right-12,top+9,AtlasGraphics.BRASS);
        g.drawString(font,text("north"),right-31,top+5,INK,false);
        heading(g,font,text("legend"),legendX,top+GAP,legendWidth);
        int y=legendListY,capacity=Math.max(0,(legendKeyY-GAP-legendListY)/LEGEND_ROW);
        if (legendEntries.size()>capacity && capacity>1) capacity--;
        legendOffset=Math.clamp(legendOffset,0,Math.max(0,legendEntries.size()-Math.max(1,capacity)));
        for (int i=legendOffset;i<legendEntries.size()&&i<legendOffset+capacity;i++) {
            Cell c=legendEntries.get(i);
            g.fill(legendX,y,legendX+9,y+9,BORDER); g.fill(legendX+1,y+1,legendX+8,y+8,subdued(c.color()));
            // A square denotes a sovereign kingdom; a notch denotes an independent city-state.
            if (c.sovereign()==null) g.fill(legendX+3,y,legendX+6,y+2,INK);
            g.drawString(font,legendNames.get(i),legendX+15,y+1,INK,false);
            y+=LEGEND_ROW;
        }
        if (legendEntries.size()>capacity&&legendKeyY-GAP-y>=LINE)
            g.drawString(font,ellipsize(font,text("legend_more",legendEntries.size()),legendWidth),legendX,legendKeyY-GAP-LINE,MUTED,false);
        g.fill(legendX,legendKeyY-GAP/2,width-MARGIN,legendKeyY-GAP/2+1,AtlasGraphics.ETCH);
        for (int i=0;i<LEGEND_KEYS.length;i++) {
            int keyY=legendKeyY+i*(LINE+2);
            g.fill(legendX,keyY,legendX+8,keyY+8,i==0?GOLD:i==1?SELECTED:BORDER);
            g.fill(legendX+1,keyY+1,legendX+7,keyY+7,i==2?0xff303936:RECESS);
            g.drawString(font,ellipsize(font,KingdomUi.text(LEGEND_KEYS[i]),legendWidth-13),legendX+13,keyY,MUTED,false);
        }
        renderSelection(g);
        g.drawString(font,scale,scaleX,10,MUTED,false);
        super.render(g,mx,my,delta);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if (x > right+7 && x < width-8 && y >= top && y < bottom) {
            legendOffset = Math.max(0, legendOffset - (int)Math.signum(vertical)); return true;
        }
        if (!inside(x,y)) return super.mouseScrolled(x,y,horizontal,vertical);
        double cx=(x-originX())/zoom, cz=(y-originZ())/zoom;
        zoom=Math.clamp(zoom*Math.pow(1.2,vertical),MIN_ZOOM,MAX_ZOOM);
        centerX=cx-(x-(8+right)/2.0)/zoom; centerZ=cz-(y-(top+bottom)/2.0)/zoom;
        return true;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && inside(x,y)) { setFocused(canvas); panning = true; dragged = false; pressX = x; pressY = y; return true; }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && panning) {
            panning = false;
            if (!dragged && inside(x,y) && !actionPending) select((int)Math.floor((x-originX())/zoom),(int)Math.floor((y-originZ())/zoom));
            return true;
        }
        return super.mouseReleased(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if(button==0 && panning) {
            if (Math.hypot(x-pressX,y-pressY) > 3) dragged = true;
            if (dragged) { centerX-=dx/zoom; centerZ-=dy/zoom; }
            return true;
        }
        return super.mouseDragged(x,y,button,dx,dy);
    }
    private static void outline(GuiGraphics g,int l,int t,int r,int b,int color) {
        outline(g,l,t,r,b,color,2);
    }
    private static void outline(GuiGraphics g,int l,int t,int r,int b,int color,int edge) {
        if (r<=l || b<=t) return;
        edge=Math.min(edge,Math.min(r-l,b-t));
        g.fill(l,t,r,t+edge,color); g.fill(l,b-edge,r,b,color); g.fill(l,t,l+edge,b,color); g.fill(r-edge,t,r,b,color);
    }
    private boolean standingInSelection() {
        return selected && minecraft.player != null && minecraft.level != null && selectedDimension.equals(minecraft.level.dimension().location())
                && minecraft.player.chunkPosition().x == selectedX && minecraft.player.chunkPosition().z == selectedZ;
    }
    private void select(int x,int z) {
        selected = true; selectedX = x; selectedZ = z; selectedDimension = minecraft.level.dimension().location();
        details = null; needsDetails = true; detailsRequested = 0; feedback = Component.empty(); actionFailed=false;
    }
    private void sendDetails(Action action) {
        if (!selected || minecraft.player == null || minecraft.level == null) return;
        boolean mutation = action == Action.CLAIM || action == Action.UNCLAIM;
        if (!mutation && !KingdomUi.canRead()) { needsDetails = true; return; }
        if (mutation && (!manageMode || !territoryAction.active || !KingdomUi.canMutate())) return;
        if (!ClientPlayNetworking.canSend(KingdomManagementPayloads.Request.TYPE)) { needsDetails = false; feedback = KingdomUi.text("unavailable"); return; }
        needsDetails = false; detailsSequence = KingdomUi.nextSequence();
        detailsAt = detailsRequested = System.currentTimeMillis();
        actionPending = mutation;
        if (mutation) KingdomUi.mutationSent(); else KingdomUi.readSent();
        if (actionPending) { territoryAction.active = false; feedback = KingdomUi.text("working"); }
        ClientPlayNetworking.send(new KingdomManagementPayloads.Request(detailsSequence,action,viewedCity,selectedDimension,selectedX,selectedZ));
    }
    public void acceptManagement(KingdomManagementPayloads.State reply) {
        if (reply.sequence() != detailsSequence || minecraft.level == null || !selectedDimension.equals(minecraft.level.dimension().location())) return;
        detailsRequested = 0; actionPending = false;
        if (reply.selection() != null && reply.selection().x() == selectedX && reply.selection().z() == selectedZ
                && reply.selection().dimension().equals(selectedDimension)) details = reply.selection();
        else details = null;
        if (reply.city() != null || reply.result().isEmpty()) {
            management=reply; ownSettlement=reply.ownSettlement(); viewedCity=reply.city()==null?null:reply.city().id();
            if (parent!=null) parent.update(reply);
        }
        feedback = reply.result().isEmpty() ? Component.empty() : Component.translatable(reply.result());
        actionFailed=!reply.success()&&!reply.result().isEmpty();
        if (reply.success()) {
            sequence = KingdomUi.nextSequence(); // Invalidate any map reply requested before this mutation.
            // The registry has changed. Wait for a fresh bounded snapshot before enabling another action.
            snapshot = null; cells.clear(); state = "loading"; fitNextSnapshot = false; refreshDue = true;
        }
        updateActions();
    }
    private boolean releasing() {
        return details!=null&&management!=null&&management.city()!=null&&management.city().id().equals(details.settlement());
    }
    private void updateActions() {
        territoryAction.visible = manageMode;
        territoryAction.style(releasing()?ButtonStyle.DANGER:ButtonStyle.PRIMARY);
        territoryAction.setMessage(KingdomUi.text(releasing() ? "unclaim" : "claim"));
        boolean ready = manageMode && details != null && KingdomUi.canMutate() && !actionPending
                && detailsRequested == 0 && !needsDetails && standingInSelection() && snapshot != null
                && snapshot.status() != 2 && !state.equals("stale");
        territoryAction.active = ready && (releasing() ? details.unclaimReason() : details.claimReason()).isEmpty();
    }
    /** One message owns action feedback: no parallel claim/unclaim reasons or repeated restrictions. */
    private Component selectionMessage() {
        if (!feedback.getString().isEmpty()) return feedback;
        if (actionPending) return KingdomUi.text("working");
        if (!manageMode) return KingdomUi.text("view_hint");
        if (management != null && management.city() != null && !management.city().manager()) return KingdomUi.text("visitor_access");
        if (management != null && ownSettlement == null) return KingdomUi.text("manage_no_kingdom");
        if (!standingInSelection()) return KingdomUi.text("stand_in_chunk");
        if (!KingdomUi.canMutate()) return KingdomUi.text("rate_limit");
        if (state.equals("error") || state.equals("stale")) return text(state);
        if (details == null || needsDetails || detailsRequested != 0 || snapshot == null) return KingdomUi.text("refresh_selection");
        String reason = releasing() ? details.unclaimReason() : details.claimReason();
        return reason.isEmpty() ? KingdomUi.text(releasing() ? "unclaim_ready" : "claim_ready") : Component.translatable(reason);
    }
    private Component selectedOwner() {
        return details==null?KingdomUi.text(needsDetails || detailsRequested!=0?"loading":"details_error")
                :details.owner().isEmpty()?Component.translatable("message.regnum-nations.territory.wilderness"):Component.literal(details.owner());
    }
    private void renderSelection(GuiGraphics g) {
        int inspectorX=MARGIN+GAP;
        AtlasGraphics.divider(g,MARGIN+4,inspectorY-4,width-(MARGIN+4)*2);
        Component line=selected?KingdomUi.text("chunk_owner",selectedX,selectedZ,selectedOwner()):KingdomUi.text("select_chunk");
        int lineWidth=manageMode?territoryAction.getX()-GAP-inspectorX:width-inspectorX*2-16;
        g.drawString(font,ellipsize(font,line,lineWidth),inspectorX,inspectorY+4,INK,false);
        Component political=details==null?selectedOwner():details.owner().isEmpty()?Component.empty():details.sovereign()==null?KingdomUi.text("independent_city"):
                KingdomUi.text("sovereign_kingdom",details.politicalName());
        // Feedback stays visible; instructions and action restrictions remain in narration.
        boolean hasFeedback=!feedback.getString().isEmpty();
        g.drawString(font,ellipsize(font,hasFeedback?feedback:political,lineWidth),inspectorX,inspectorY+4+LINE,hasFeedback?(actionFailed?ERROR:GOLD):MUTED,false);
    }
    private final class MapCanvas extends AbstractWidget {
        MapCanvas() { super(8,top,right-8,bottom-top,KingdomUi.text("map_keyboard")); }
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta) {
            if (isFocused()) outline(g,getX()+1,getY()+1,getRight()-1,getBottom()-1,AtlasGraphics.BRASS);
        }
        @Override public boolean keyPressed(int key,int scan,int modifiers) {
            int dx=key==GLFW.GLFW_KEY_LEFT?-1:key==GLFW.GLFW_KEY_RIGHT?1:0;
            int dz=key==GLFW.GLFW_KEY_UP?-1:key==GLFW.GLFW_KEY_DOWN?1:0;
            if (dx!=0 || dz!=0) {
                if (Screen.hasControlDown()) { centerX+=dx; centerZ+=dz; }
                else if (!actionPending) {
                    select(selectedX+dx,selectedZ+dz);
                    double sx=originX()+(selectedX+0.5)*zoom,sz=originZ()+(selectedZ+0.5)*zoom;
                    if (!inside(sx,sz)) { centerX=selectedX+0.5; centerZ=selectedZ+0.5; }
                }
                return true;
            }
            if (key==GLFW.GLFW_KEY_HOME) { fitClaims(); return true; }
            return super.keyPressed(key,scan,modifiers);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE,selected?KingdomUi.text("chunk_owner",selectedX,selectedZ,
                    details==null?KingdomUi.text("loading"):details.owner().isEmpty()?Component.translatable("message.regnum-nations.territory.wilderness"):Component.literal(details.owner())):getMessage());
            output.add(NarratedElementType.USAGE,getMessage());
            var hint=selectionMessage().copy();
            if (details!=null) {
                if (!details.owner().isEmpty()) hint.append(". ").append(details.sovereign()==null?KingdomUi.text("independent_city"):KingdomUi.text("sovereign_kingdom",details.politicalName()));
                hint.append(". ").append(KingdomUi.text("adjacency",KingdomUi.text(details.adjacent()?"yes":"no")));
            }
            output.add(NarratedElementType.HINT,hint);
        }
    }
    private void showOverview() {
        // Reopen with a fresh on-demand read, including actions completed after navigation.
        KingdomManagementScreen menu = parent == null ? new KingdomManagementScreen(management) : parent;
        menu.refreshOnOpen(); minecraft.setScreen(menu);
    }
    @Override public void onClose() {
        if (parent == null) minecraft.setScreen(null);
        else { parent.refreshOnOpen(); minecraft.setScreen(parent); }
    }
}
