# Novedades de la versión 0.9.29

---

## 📱 Stock de teléfonos

- Los móviles **sin grado** (grado «n/a» en NSYS) ya tienen **SKU**: el de su modelo, capacidad y color acabado en **NA**
  (por ejemplo `i13128AzulNA`; en un eSIM, `i14eSIM128MedianocheNA`). Antes salían con una raya.
- Al buscar por SKU salen junto a los demás grados de su modelo (`i13128Azul`), o solos si buscas el SKU entero
  (`i13128AzulNA`).
- Siguen sin SKU los móviles cuyo producto no está en el maestro. El día que el maestro traiga un SKU propio para los n/a,
  se usará ese.
