import os
import sys  # Code Smell: Importación no utilizada
import math # Code Smell: Importación no utilizada

# Code Smell: Nombre de clase en minúsculas (debería ser PascalCase)
class calculadora_operaciones:

    # Code Smell: Uso de camelCase en lugar de snake_case para métodos en Python
    def calcularTotalVentas(self, precio, cantidad):
        total = precio * cantidad
        return total
        print("El cálculo ha terminado")  # Bug: Código inalcanzable (después del return)

    # Duplicación: Esta función es exactamente igual a la de arriba
    def calcular_total_compras(self, precio, cantidad):
        total = precio * cantidad
        return total
        print("El cálculo ha terminado")

    def operacion_muy_compleja(self, valor):
        # Code Smell: Variables con nombres de una sola letra sin contexto claro
        x = 10
        y = 20
        
        # Code Smell: Complejidad innecesaria
        if x == 10:
            if y == 20:
                if valor > 0:
                    for i in range(10):
                        if i == 5:
                            # Bug / Reliability: 'resultado' no está definido globalmente 
                            # y fallará si entra aquí, pero el linter estático lo nota.
                            return resultado  

        return False