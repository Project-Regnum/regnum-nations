#!/usr/bin/env python3
"""Continuous clearance proof for the exported spin/bob against the artist's frame.

Sample at half-tick intervals, use oriented-box separating axes, then subtract a
maximum vertex-travel bound for the interval between samples. Standard library.
"""
import itertools
import json
import math
from obelisk_assets import ASSETS, read_source


def dot(a, b): return sum(x*y for x,y in zip(a,b))
def sub(a, b): return tuple(x-y for x,y in zip(a,b))
def add(a, b): return tuple(x+y for x,y in zip(a,b))
def cross(a, b): return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])


def rotate(v, axis, degrees):
    angle=math.radians(degrees); c,s=math.cos(angle),math.sin(angle)
    x,y,z=v
    if axis=='x': return (x,y*c-z*s,y*s+z*c)
    if axis=='y': return (x*c+z*s,y,-x*s+z*c)
    return (x*c-y*s,x*s+y*c,z)


def obb(element):
    center=tuple((a+b)/2 for a,b in zip(element['from'],element['to']))
    half=tuple((b-a)/2 for a,b in zip(element['from'],element['to']))
    axes=((1,0,0),(0,1,0),(0,0,1))
    if 'rotation' in element:
        r=element['rotation']; origin=r['origin']
        center=add(origin,rotate(sub(center,origin),r['axis'],r['angle']))
        axes=tuple(rotate(a,r['axis'],r['angle']) for a in axes)
    return center,axes,half


def aabb(box):
    c,axes,h=box
    radii=tuple(sum(abs(axes[j][i])*h[j] for j in range(3)) for i in range(3))
    return tuple(c[i]-radii[i] for i in range(3)),tuple(c[i]+radii[i] for i in range(3))


def separation(a, b):
    ca,aa,ha=a; cb,ab,hb=b
    alo,ahi=aabb(a); blo,bhi=aabb(b)
    broad=max(max(blo[i]-ahi[i],alo[i]-bhi[i]) for i in range(3))
    if broad > .5: return broad
    distance=sub(cb,ca); best=-math.inf
    for axis in list(aa)+list(ab)+[cross(x,y) for x in aa for y in ab]:
        length=math.sqrt(dot(axis,axis))
        if length<1e-10: continue
        axis=tuple(v/length for v in axis)
        ra=sum(ha[i]*abs(dot(aa[i],axis)) for i in range(3))
        rb=sum(hb[i]*abs(dot(ab[i],axis)) for i in range(3))
        best=max(best,abs(dot(distance,axis))-ra-rb)
    return best


def validate():
    config=json.loads((ASSETS/'regnum-nations/obelisk_animation.json').read_text())
    for kind in ('city','kingdom'):
        source,solid,crystal,images,pivot,ids,digest=read_source(kind)
        assert config[kind]['source_sha256']==digest
        assert config[kind]['crystal_uuids']==ids
        profile=config[kind]
        spin,bob,amplitude=profile['rotation_ticks'],profile['bob_ticks'],profile['bob_amplitude']
        moving=obb(crystal[0]); static=[(e['name'],obb(e)) for e in solid]
        center,axes,half=moving
        assert max(abs(a-b) for a,b in zip(center,pivot))<1e-7, 'Crystal must spin about its authored center'
        assert spin % bob == 0, 'Bob and glow must share the full rotation loop'
        radial=0
        for signs in itertools.product((-1,1),repeat=3):
            vertex=tuple(center[j]+sum(signs[i]*axes[i][j]*half[i] for i in range(3)) for j in range(3))
            radial=max(radial,math.hypot(vertex[0]-pivot[0],vertex[2]-pivot[2]))
        # Nearest half-tick sample is at most a quarter-tick away. Subtract the
        # maximum possible motion of any crystal vertex over that time.
        travel=(radial*2*math.pi/spin+amplitude*2*math.pi/bob)*.25
        closest=(math.inf,'',0)
        for sample in range(spin*2):
            time=sample/2
            angle=time*360/spin
            rise=math.sin(time*2*math.pi/bob)*amplitude
            c=add(pivot,rotate(sub(center,pivot),'y',angle))
            c=(c[0],c[1]+rise,c[2])
            frame=(c,tuple(rotate(a,'y',angle) for a in axes),half)
            for name,stationary in static:
                gap=separation(frame,stationary)
                assert gap>travel, f'{kind} crystal may intersect {name} at tick {time}: {gap:g}'
                if gap<closest[0]: closest=(gap,name,time)
        print(f'PASS {kind}: {spin/20:g}s seamless loop, {spin*2} samples; '
              f'continuous clearance >= {closest[0]-travel:.4f} model units; nearest {closest[1]}')


if __name__=='__main__':
    validate()
