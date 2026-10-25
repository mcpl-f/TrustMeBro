-- Save a named trust region with its owner, world, center, and radius.
INSERT INTO regions(
  name, mutable, created_by_mc_name, created_at, wid, center_x, center_z, radius
) VALUES (:name, :mutable, :createdBy, :createdAt, :wid, :centerX, :centerZ, :radius)
RETURNING id
