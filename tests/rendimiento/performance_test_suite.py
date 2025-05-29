#!/usr/bin/env python3
"""
Suite Completa de Pruebas de Rendimiento - E-commerce Microservices
=================================================================

Este script maestro permite ejecutar todas las pruebas de rendimiento
de manera coordinada y generar reportes unificados.

Pruebas incluidas:
1. Carga en Listado de Productos (product-service via proxy-client)
2. Capacidad de Respuesta del User Service (user-service via proxy-client)

Uso:
    # Ejecutar todas las pruebas secuencialmente
    python performance_test_suite.py --all

    # Ejecutar prueba específica
    python performance_test_suite.py --test products
    python performance_test_suite.py --test users

    # Ejecutar con configuración personalizada
    python performance_test_suite.py --test products --users 50 --spawn-rate 5 --duration 300

    # Generar reporte comparativo
    python performance_test_suite.py --report
"""

import argparse
import subprocess
import sys
import time
import json
import os
from datetime import datetime
from typing import Dict, List, Any
import concurrent.futures


class PerformanceTestSuite:
    """Suite de pruebas de rendimiento para microservicios de e-commerce"""
    
    def __init__(self, host: str = "http://host.docker.internal"):
        self.host = host
        self.test_files = {
            "products": "product_listing_load_test.py",
            "users": "user_service_load_test.py"
        }
        self.results_dir = "performance_results"
        self.ensure_results_directory()
        
    def ensure_results_directory(self):
        """Crea el directorio de resultados si no existe"""
        if not os.path.exists(self.results_dir):
            os.makedirs(self.results_dir)
    
    def run_single_test(self, test_name: str, users: int = 10, spawn_rate: int = 2, 
                       duration: int = 60, headless: bool = True) -> Dict[str, Any]:
        """
        Ejecuta una prueba de rendimiento específica
        
        Args:
            test_name: Nombre de la prueba (products, users)
            users: Número de usuarios concurrentes
            spawn_rate: Velocidad de generación de usuarios
            duration: Duración de la prueba en segundos
            headless: Si ejecutar sin interfaz web
            
        Returns:
            Dict con resultados de la prueba
        """
        if test_name not in self.test_files:
            raise ValueError(f"Test '{test_name}' not found. Available tests: {list(self.test_files.keys())}")
        
        test_file = self.test_files[test_name]
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        results_file = f"{self.results_dir}/{test_name}_results_{timestamp}.json"
        
        # Construir comando de Locust
        cmd = [
            "locust",
            "-f", test_file,
            "--host", self.host,
            "--users", str(users),
            "--spawn-rate", str(spawn_rate),
            "--run-time", f"{duration}s",
            "--html", f"{self.results_dir}/{test_name}_report_{timestamp}.html",
            "--csv", f"{self.results_dir}/{test_name}_stats_{timestamp}"
        ]
        
        if headless:
            cmd.append("--headless")
        
        print(f"🚀 Ejecutando prueba: {test_name}")
        print(f"📊 Configuración: {users} usuarios, {spawn_rate} spawn rate, {duration}s duración")
        print(f"🔗 Host: {self.host}")
        print(f"📄 Comando: {' '.join(cmd)}")
        print("-" * 80)
        
        start_time = time.time()
        
        try:
            # Ejecutar Locust
            result = subprocess.run(cmd, capture_output=True, text=True, cwd=os.path.dirname(__file__))
            
            end_time = time.time()
            execution_time = end_time - start_time
            
            # Recopilar resultados
            test_result = {
                "test_name": test_name,
                "timestamp": timestamp,
                "configuration": {
                    "users": users,
                    "spawn_rate": spawn_rate,
                    "duration": duration,
                    "host": self.host
                },
                "execution_time": execution_time,
                "return_code": result.returncode,
                "stdout": result.stdout,
                "stderr": result.stderr,
                "files_generated": {
                    "html_report": f"{test_name}_report_{timestamp}.html",
                    "csv_stats": f"{test_name}_stats_{timestamp}_stats.csv",
                    "csv_history": f"{test_name}_stats_{timestamp}_stats_history.csv",
                    "csv_failures": f"{test_name}_stats_{timestamp}_failures.csv"
                }
            }
            
            # Guardar resultados en JSON
            with open(results_file, 'w') as f:
                json.dump(test_result, f, indent=2)
            
            if result.returncode == 0:
                print(f"✅ Prueba {test_name} completada exitosamente")
                print(f"⏱️  Tiempo de ejecución: {execution_time:.2f} segundos")
            else:
                print(f"❌ Prueba {test_name} falló con código: {result.returncode}")
                print(f"🔍 Error: {result.stderr}")
            
            return test_result
            
        except Exception as e:
            error_result = {
                "test_name": test_name,
                "timestamp": timestamp,
                "error": str(e),
                "execution_time": time.time() - start_time
            }
            
            with open(results_file, 'w') as f:
                json.dump(error_result, f, indent=2)
            
            print(f"💥 Error ejecutando prueba {test_name}: {e}")
            return error_result
    
    def run_all_tests(self, users: int = 10, spawn_rate: int = 2, duration: int = 60) -> List[Dict[str, Any]]:
        """
        Ejecuta todas las pruebas de rendimiento secuencialmente
        
        Args:
            users: Número de usuarios concurrentes para cada prueba
            spawn_rate: Velocidad de generación de usuarios
            duration: Duración de cada prueba en segundos
            
        Returns:
            Lista con resultados de todas las pruebas
        """
        print("🎯 Iniciando Suite Completa de Pruebas de Rendimiento")
        print("=" * 80)
        
        all_results = []
        
        for test_name in self.test_files.keys():
            print(f"\n📋 Preparando prueba: {test_name}")
            
            # Pausa entre pruebas para permitir que el sistema se estabilice
            if all_results:  # No pausar antes de la primera prueba
                print("⏸️  Pausa de 30 segundos entre pruebas...")
                time.sleep(30)
            
            result = self.run_single_test(test_name, users, spawn_rate, duration)
            all_results.append(result)
            
            print(f"✅ Prueba {test_name} finalizada\n")
        
        # Generar reporte resumen
        self._generate_summary_report(all_results)
        
        print("🏁 Suite de pruebas completada")
        print(f"📊 Resultados guardados en: {self.results_dir}/")
        
        return all_results
    
    def run_parallel_tests(self, users: int = 10, spawn_rate: int = 2, duration: int = 60) -> List[Dict[str, Any]]:
        """
        Ejecuta todas las pruebas de rendimiento en paralelo
        ADVERTENCIA: Esto puede sobrecargar significativamente el sistema
        """
        print("⚡ Iniciando Pruebas de Rendimiento en PARALELO")
        print("⚠️  ADVERTENCIA: Esto puede sobrecargar el sistema")
        print("=" * 80)
        
        all_results = []
        
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
            futures = {
                executor.submit(self.run_single_test, test_name, users, spawn_rate, duration): test_name
                for test_name in self.test_files.keys()
            }
            
            for future in concurrent.futures.as_completed(futures):
                test_name = futures[future]
                try:
                    result = future.result()
                    all_results.append(result)
                    print(f"✅ Prueba paralela {test_name} finalizada")
                except Exception as e:
                    print(f"❌ Error en prueba paralela {test_name}: {e}")
        
        # Generar reporte resumen
        self._generate_summary_report(all_results)
        
        return all_results
    
    def _generate_summary_report(self, results: List[Dict[str, Any]]):
        """Genera un reporte resumen de todas las pruebas"""
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        summary_file = f"{self.results_dir}/summary_report_{timestamp}.json"
        
        summary = {
            "suite_execution": {
                "timestamp": timestamp,
                "total_tests": len(results),
                "successful_tests": sum(1 for r in results if r.get("return_code") == 0),
                "failed_tests": sum(1 for r in results if r.get("return_code") != 0),
                "total_execution_time": sum(r.get("execution_time", 0) for r in results)
            },
            "test_results": results
        }
        
        with open(summary_file, 'w') as f:
            json.dump(summary, f, indent=2)
        
        print(f"📋 Reporte resumen generado: {summary_file}")
    
    def generate_comparison_report(self):
        """Genera un reporte comparativo de múltiples ejecuciones"""
        # Buscar todos los archivos de resultados
        result_files = [f for f in os.listdir(self.results_dir) if f.endswith('.json') and 'summary' not in f]
        
        if not result_files:
            print("❌ No se encontraron archivos de resultados para comparar")
            return
        
        print(f"📊 Generando reporte comparativo de {len(result_files)} ejecuciones...")
        
        # Aquí iría la lógica para analizar y comparar resultados
        # Por ahora, simplemente listamos los archivos disponibles
        print("📄 Archivos de resultados encontrados:")
        for file in sorted(result_files):
            print(f"  - {file}")


def main():
    """Función principal del script"""
    parser = argparse.ArgumentParser(description="Suite de Pruebas de Rendimiento E-commerce")
    
    parser.add_argument("--test", choices=["products", "users", "all"], 
                       help="Prueba específica a ejecutar")
    parser.add_argument("--all", action="store_true", help="Ejecutar todas las pruebas")
    parser.add_argument("--parallel", action="store_true", help="Ejecutar pruebas en paralelo")
    parser.add_argument("--host", default="http://host.docker.internal", help="Host del sistema bajo prueba")
    parser.add_argument("--users", type=int, default=10, help="Número de usuarios concurrentes")
    parser.add_argument("--spawn-rate", type=int, default=2, help="Velocidad de generación de usuarios")
    parser.add_argument("--duration", type=int, default=60, help="Duración de la prueba en segundos")
    parser.add_argument("--report", action="store_true", help="Generar reporte comparativo")
    
    args = parser.parse_args()
    
    # Crear suite de pruebas
    suite = PerformanceTestSuite(host=args.host)
    
    if args.report:
        suite.generate_comparison_report()
        return
    
    if args.all or args.test == "all":
        if args.parallel:
            suite.run_parallel_tests(args.users, args.spawn_rate, args.duration)
        else:
            suite.run_all_tests(args.users, args.spawn_rate, args.duration)
    elif args.test:
        suite.run_single_test(args.test, args.users, args.spawn_rate, args.duration)
    else:
        # Mostrar ayuda si no se especifica ninguna acción
        parser.print_help()
        print("\n🎯 Ejemplos de uso:")
        print("  python performance_test_suite.py --test products")
        print("  python performance_test_suite.py --all --users 50 --duration 300")
        print("  python performance_test_suite.py --parallel --users 25 --duration 180")


if __name__ == "__main__":
    main()
