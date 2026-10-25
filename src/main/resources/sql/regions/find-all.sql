-- Load named trust regions in stable creation order for region matching and display.
SELECT id, name, mutable, created_by_mc_name, created_at,
       wid, center_x, center_z, radius
FROM regions
ORDER BY id ASC
