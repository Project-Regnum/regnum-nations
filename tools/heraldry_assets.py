#!/usr/bin/env python3
"""Original polygon artwork and static wooden supports. No downloaded artwork or image library."""
import json, math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
ASSETS=ROOT/'assets/regnum-nations'
def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,indent=2)+'\n')
def poly(points): return [list(p) for p in points]
def circle(x,y,r,n=16): return [[round(x+math.sin(i*math.tau/n)*r,3),round(y+math.cos(i*math.tau/n)*r,3)] for i in range(n)]
def star(x,y,r,inner,n=5): return [[round(x+math.sin(i*math.pi/n)*(r if i%2==0 else inner),3),round(y-math.cos(i*math.pi/n)*(r if i%2==0 else inner),3)] for i in range(2*n)]
shields={
 'heater':[[4,4],[96,4],[95,48],[87,70],[70,89],[50,99],[30,89],[13,70],[5,48]],
 'kite':[[50,1],[93,16],[88,47],[73,75],[50,100],[27,75],[12,47],[7,16]],
 'round':[[x,50+(y-50)*.8] for x,y in circle(50,50,48,40)],
 'french':[[4,4],[96,4],[96,69],[87,82],[64,89],[50,99],[36,89],[13,82],[4,69]],
 'bouche':[[4,4],[96,4],[96,14],[80,18],[78,28],[95,34],[91,60],[77,82],[50,99],[23,82],[9,60]],
}
# Independent silhouettes, designed on a 100-unit square. Separate polygons form a single charge.
charges={
 'crown':{'solid':[[[10,28],[30,44],[50,17],[70,44],[90,28],[81,72],[19,72]],[[20,77],[80,77],[80,85],[20,85]],circle(10,24,5),circle(50,12,5),circle(90,24,5)],'holes':[[[29,57],[34,57],[34,65],[29,65]],[[47,53],[53,53],[53,65],[47,65]],[[66,57],[71,57],[71,65],[66,65]]]},
 'sword':{'solid':[[[50,4],[57,19],[55,61],[45,61],[43,19]],[[24,60],[76,60],[76,68],[24,68]],[[46,66],[54,66],[54,85],[46,85]],circle(50,90,7)]},
 'tower':{'solid':[[[22,13],[34,13],[34,24],[44,24],[44,13],[56,13],[56,24],[66,24],[66,13],[78,13],[78,37],[72,41],[72,81],[81,81],[81,91],[19,91],[19,81],[28,81],[28,41],[22,37]]], 'holes':[[[44,91],[44,71],[50,65],[56,71],[56,91]],[[46,42],[54,42],[54,54],[46,54]]]},
 'star':{'solid':[star(50,50,46,20)]},
 'sun':{'solid':[star(50,50,48,29,12),circle(50,50,24,24)],'holes':[circle(50,50,18,24)]},
 'rose':{'solid':[circle(50+math.sin(i*math.tau/5)*22,50-math.cos(i*math.tau/5)*22,23) for i in range(5)],'holes':[circle(50,50,9),[[48,17],[52,17],[52,32],[48,32]],[[70,40],[83,35],[85,39],[72,44]],[[63,68],[72,80],[68,83],[59,71]],[[37,68],[32,83],[28,80],[33,66]],[[28,44],[15,39],[17,35],[30,40]]]},
 'fleur_de_lis':{'solid':[[[50,3],[65,24],[62,40],[54,57],[57,73],[66,85],[55,82],[50,96],[45,82],[34,85],[43,73],[46,57],[38,40],[35,24]],[[40,62],[22,54],[10,38],[12,25],[25,23],[35,33],[40,49],[46,58]],[[60,62],[78,54],[90,38],[88,25],[75,23],[65,33],[60,49],[54,58]],[[29,60],[71,60],[71,68],[29,68]]]},
 'eagle':{'solid':[[[43,37],[42,24],[48,18],[62,18],[68,24],[57,27],[56,41],[65,58],[59,74],[67,92],[51,85],[34,92],[41,73],[35,58]],[[43,44],[30,36],[22,14],[14,6],[15,33],[5,22],[8,47],[2,39],[10,64],[24,68],[38,59]],[[57,44],[70,36],[78,14],[86,6],[85,33],[95,22],[92,47],[98,39],[90,64],[76,68],[62,59]],[[40,68],[32,79],[21,81],[20,88],[35,87],[48,74]],[[60,68],[68,79],[79,81],[80,88],[65,87],[52,74]]]},
 'lion':{'solid':[[[32,8],[37,2],[44,8],[53,6],[57,14],[67,17],[65,25],[53,29],[54,39],[46,35],[39,42],[33,34],[26,35],[29,27],[23,23],[28,18],[25,12]],[[39,35],[49,34],[48,47],[61,57],[59,70],[49,76],[43,65],[34,60],[31,47]],[[38,41],[26,40],[20,30],[11,28],[13,36],[11,43],[27,51],[36,49]],[[49,43],[62,35],[67,23],[76,18],[81,21],[74,27],[72,44],[52,58]],[[43,64],[44,74],[30,85],[15,85],[8,92],[33,94],[53,78]],[[57,64],[61,73],[58,82],[66,87],[79,85],[85,91],[64,96],[48,86],[47,75]],[[58,60],[73,61],[85,49],[85,32],[78,22],[81,15],[88,13],[92,20],[91,31],[95,41],[92,56],[78,71],[60,72]]],'holes':[circle(53,16,2.7)]},
 'wolf':{'solid':[[[13,58],[19,38],[35,33],[57,39],[64,31],[63,10],[76,23],[85,20],[85,31],[98,38],[87,46],[77,47],[68,64],[60,70],[66,89],[56,94],[47,72],[37,65],[27,72],[26,90],[15,93],[18,65]],[[20,48],[11,42],[6,23],[1,26],[1,47],[11,63],[22,61]]],'holes':[circle(79,31,2.5)]},
 'stag':{'solid':[[[50,28],[37,21],[25,24],[31,35],[40,39],[43,59],[35,71],[40,89],[50,96],[60,89],[65,71],[57,59],[60,39],[69,35],[75,24],[63,21]],[[40,28],[29,19],[27,4],[21,2],[21,13],[11,8],[8,13],[23,24],[38,35]],[[60,28],[71,19],[73,4],[79,2],[79,13],[89,8],[92,13],[77,24],[62,35]]],'holes':[[[45,48],[48,51],[45,53]],[[55,48],[52,51],[55,53]],[[46,78],[54,78],[50,82]]]},
}
for name,points in shields.items(): write(ASSETS/f'heraldry/shields/{name}.json',{'solid':[points]})
for name,shape in charges.items(): write(ASSETS/f'heraldry/charges/{name}.json',shape)
# Block supports use Minecraft's bundled material textures, not vanilla banner rendering/patterns.
def box(a,b,texture): return {'from':a,'to':b,'faces':{d:{'texture':'#'+texture} for d in ['north','south','east','west','up','down']}}
for wall in [False,True]:
 name='wall_royal_banner' if wall else 'royal_banner'
 elements=([box([7,0,7],[9,16,9],'wood'),box([1,14,7],[15,16,9],'gold')] if not wall else [box([7,12,12],[9,14,16],'wood'),box([1,14,12],[15,16,14],'gold')])
 write(ASSETS/f'models/block/{name}.json',{'textures':{'wood':'minecraft:block/dark_oak_planks','gold':'minecraft:block/gold_block','particle':'minecraft:block/dark_oak_planks'},'elements':elements})
 write(ASSETS/f'blockstates/{name}.json',{'variants':{f'facing={d}':{'model':f'regnum-nations:block/{name}','y':y} for d,y in [('north',0),('east',90),('south',180),('west',270)]}})
# Item uses a custom renderer for both support and its kingdom's current arms.
write(ASSETS/'models/item/royal_banner.json',{'parent':'minecraft:builtin/entity','gui_light':'front','display':{'gui':{'rotation':[20,-30,0],'translation':[0,0,0],'scale':[0.9,0.9,0.9]},'ground':{'scale':[0.5,0.5,0.5]},'fixed':{'scale':[0.8,0.8,0.8]},'thirdperson_righthand':{'rotation':[0,90,0],'scale':[0.65,0.65,0.65]},'firstperson_righthand':{'rotation':[0,90,0],'scale':[0.65,0.65,0.65]}}})
write(ROOT/'data/regnum-nations/recipe/royal_banner.json',{'type':'minecraft:crafting_shaped','category':'decorations','pattern':['WWW','WWW',' S '],'key':{'W':{'tag':'minecraft:wool'},'S':{'item':'minecraft:stick'}},'result':{'id':'regnum-nations:royal_banner','count':1}})
if __name__=='__main__': print('Wrote 5 shields, 11 charges, two banner supports and blank-banner recipe')
