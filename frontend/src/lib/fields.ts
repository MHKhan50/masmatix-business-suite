import systemsJson from "../generated/systems.json";

export type FieldType = "string" | "text" | "int" | "decimal" | "bool" | "date" | "email" | "phone" | "enum";

export interface FieldDef {
  name: string;
  type: FieldType;
  required?: boolean;
  values?: string[];
  min?: number | string;
  max?: number;
}

export interface EntityDef {
  name: string;
  label: string;
  path: string;
  api: string;
  fields: FieldDef[];
}

export interface SystemDef {
  system: string;
  title: string;
  entities: EntityDef[];
}

/** Systems built by tools/generator from specs/*.json. */
export const generatedSystems = systemsJson as unknown as SystemDef[];

/** Hand-written POS product module, shown with the same generic screen. */
export const productEntity: EntityDef = {
  name: "Product",
  label: "Products",
  path: "products",
  api: "/api/pos/products",
  fields: [
    { name: "sku", type: "string", required: true },
    { name: "barcode", type: "string", required: true },
    { name: "name", type: "string", required: true },
    { name: "category", type: "string" },
    { name: "unitPrice", type: "decimal", required: true },
    { name: "taxRate", type: "decimal", required: true },
    { name: "stockQty", type: "int" },
    { name: "reorderLevel", type: "int" },
    { name: "active", type: "bool" },
  ],
};

export const labelOf = (name: string) =>
  name.replace(/([A-Z])/g, " $1").replace(/^./, (c) => c.toUpperCase());

/** Hand-written gym member module (renewal and entry rules live in the API), shown with the generic screen. */
export const gymMemberEntity: EntityDef = {
  name: "GymMember",
  label: "Members",
  path: "members",
  api: "/api/gym/members",
  fields: [
    { name: "memberCode", type: "string", required: true },
    { name: "fullName", type: "string", required: true },
    { name: "phone", type: "phone" },
    { name: "email", type: "email" },
    { name: "expiresOn", type: "date" },
    { name: "frozen", type: "bool" },
  ],
};
